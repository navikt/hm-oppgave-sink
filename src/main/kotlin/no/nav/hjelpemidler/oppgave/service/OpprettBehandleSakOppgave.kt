package no.nav.hjelpemidler.oppgave.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.oppgave.metrics.Prometheus
import no.nav.hjelpemidler.oppgave.oppgave.OppgaveClientV2
import no.nav.hjelpemidler.oppgave.pdl.PdlClient
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class OpprettBehandleSakOppgave(
    rapidsConnection: RapidsConnection,
    private val oppgaveClientV2: OppgaveClientV2,
    private val pdlClient: PdlClient,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-sakTilbakeførtOebs") }
            validate { it.requireKey("fnrBruker", "joarkRef", "soknadId", "eventId", "enhet", "sakId") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.joarkRef get() = this["joarkRef"].textValue()
    private val JsonMessage.soknadId get() = this["soknadId"].textValue()
    private val JsonMessage.enhet get() = this["enhet"].textValue()
    private val JsonMessage.sakId get() = this["sakId"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    if (skipEvent(UUID.fromString(packet.eventId))) {
                        logger.info { "Hopper over event i skip-list: ${packet.eventId}" }
                        return@launch
                    }
                    try {
                        val soknadData = OpprettBehandleOppgaveData(
                            fnrBruker = packet.fnrBruker,
                            joarkRef = packet.joarkRef,
                            soknadId = UUID.fromString(packet.soknadId),
                            sakId = packet.sakId,
                            enhet = packet.enhet
                        )
                        logger.info { "Tilbakeført sak mottatt, sakId:  ${soknadData.sakId}" }
                        val aktorId = pdlClient.hentAktorId(soknadData.fnrBruker)
                        val oppgaveId = opprettOppgave(aktorId, soknadData.joarkRef, soknadData.soknadId, soknadData.enhet)
                        forward(soknadData, oppgaveId, context)
                    } catch (e: Exception) {
                        throw RuntimeException("Håndtering av event ${packet.eventId} feilet", e)
                    }
                }
            }
        }
    }

    private fun skipEvent(eventId: UUID): Boolean {
        val skipList = mutableListOf<UUID>()
        return skipList.any { it == eventId }
    }

    private suspend fun opprettOppgave(aktorId: String, journalpostId: String, soknadId: UUID, enhet: String) =
        kotlin.runCatching {
            oppgaveClientV2.opprettBehandleSakOppgave(aktorId, journalpostId, enhet)
        }.onSuccess {
            logger.info("Behandle sak oppgave opprettet: $soknadId")
            Prometheus.hentetAktorIdCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under opprettelse av behandle sak oppgave: $soknadId" }
        }.getOrThrow()

    private fun CoroutineScope.forward(opprettBehandleOppgaveData: OpprettBehandleOppgaveData, joarkRef: String, context: MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(opprettBehandleOppgaveData.fnrBruker, opprettBehandleOppgaveData.toJson(joarkRef, "hm-opprettetBehandleSakOppgave"))
            Prometheus.oppgaveOpprettetCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Behandle sak oppgave opprettet for søknad: ${opprettBehandleOppgaveData.soknadId}")
                    sikkerlogg.info("Behandle sak oppgave opprettet for søknad: ${opprettBehandleOppgaveData.soknadId}, fnr: ${opprettBehandleOppgaveData.fnrBruker})")
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    logger.error("Failed: ${it.message}. Soknad: ${opprettBehandleOppgaveData.soknadId}")
                }
            }
        }
    }
}

internal data class OpprettBehandleOppgaveData(
    val fnrBruker: String,
    val soknadId: UUID,
    val joarkRef: String,
    val sakId: String,
    val enhet: String
) {
    internal fun toJson(oppgaveId: String, producedEventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["soknadId"] = this.soknadId
            it["eventName"] = producedEventName
            it["opprettet"] = LocalDateTime.now()
            it["fnrBruker"] = this.fnrBruker
            it["oppgaveId"] = oppgaveId
            it["enhet"] = enhet
            it["sakId"] = sakId
        }.toJson()
    }
}
