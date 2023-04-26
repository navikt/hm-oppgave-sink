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
import no.nav.hjelpemidler.oppgave.oppgave.OppgaveClient
import no.nav.hjelpemidler.oppgave.pdl.PdlClient
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

internal class OpprettJournalføringsoppgaveEtterFeilregistreringAvSakstilknytning(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveClient,
    private val pdlClient: PdlClient,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-opprettetMottattJournalpost") }
            validate {
                it.requireKey(
                    "fodselNrBruker",
                    "joarkRef",
                    "eventId",
                    "sakId",
                    "soknadId",
                )
            }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.nyJournalpostId get() = this["joarkRef"].textValue()
    private val JsonMessage.sakId get() = this["sakId"].textValue()
    private val JsonMessage.soknadId get() = this["soknadId"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    if (skipEvent(UUID.fromString(packet.eventId))) {
                        log.info { "Hopper over event i skip-list: ${packet.eventId}" }
                        return@launch
                    }
                    try {
                        val oppgaveData = OpprettJournalføringsoppgaveEtterFeilregistreringOppgaveData(
                            fnrBruker = packet.fnrBruker,
                            nyJournalpostId = packet.nyJournalpostId,
                            sakId = packet.sakId,
                            soknadId = UUID.fromString(packet.soknadId),
                        )
                        log.info { "Tilbakeført sak mottatt, sakId=${oppgaveData.sakId}, soknadsId=${oppgaveData.soknadId}" }
                        val aktorId = pdlClient.hentAktørId(oppgaveData.fnrBruker)
                        val oppgaveId = opprettOppgave(
                            aktorId,
                            oppgaveData.nyJournalpostId,
                            oppgaveData.sakId,
                            oppgaveData.soknadId,
                        )
                        log.info("Tilbakeført oppgave opprettet med oppgaveId=$oppgaveId")
                        forward(oppgaveData, oppgaveId, context)
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

    private suspend fun opprettOppgave(
        aktorId: String,
        journalpostId: String,
        sakId: String,
        soknadId: UUID,
    ) =
        kotlin.runCatching {
            oppgaveClient.arkiverSøknad(aktorId, journalpostId)
        }.onSuccess {
            log.info("Journalføringsoppgave opprettet for tilbakeført sak=$sakId, soknadsId=$soknadId, oppgaveId=$it")
        }.onFailure {
            log.error(it) { "Feilet under opprettelse av journalføringsoppgave for sak: $sakId" }
        }.getOrThrow()

    private fun CoroutineScope.forward(
        opprettBehandleOppgaveData: OpprettJournalføringsoppgaveEtterFeilregistreringOppgaveData,
        joarkRef: String,
        context: MessageContext,
    ) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(
                opprettBehandleOppgaveData.fnrBruker,
                opprettBehandleOppgaveData.toJson(
                    joarkRef,
                    "hm-opprettetJournalføringsoppgaveForTilbakeførtSak",
                ),
            )
            Prometheus.oppgaveOpprettetCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    log.info("Journalføringsoppgave opprettet for sak: ${opprettBehandleOppgaveData.sakId}")
                }

                is CancellationException -> log.warn("Cancelled: ${it.message}")
                else -> {
                    log.error(
                        "Klarte ikke å opprette journalføringsoppgave for tilbakeført sak: " +
                            "${opprettBehandleOppgaveData.sakId}, beskjed:  ${it.message}.",
                    )
                }
            }
        }
    }
}

internal data class OpprettJournalføringsoppgaveEtterFeilregistreringOppgaveData(
    val fnrBruker: String,
    val sakId: String,
    val nyJournalpostId: String,
    val soknadId: UUID,
) {
    internal fun toJson(oppgaveId: String, producedEventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventName"] = producedEventName
            it["opprettet"] = LocalDateTime.now()
            it["fnrBruker"] = this.fnrBruker
            it["oppgaveId"] = oppgaveId
            it["sakId"] = sakId
            it["nyJournalpostId"] = nyJournalpostId
            it["soknadId"] = this.soknadId
        }.toJson()
    }
}
