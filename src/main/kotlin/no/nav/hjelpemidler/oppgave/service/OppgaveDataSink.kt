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
import no.nav.hjelpemidler.oppgave.Configuration
import no.nav.hjelpemidler.oppgave.client.OppgaveClient
import no.nav.hjelpemidler.oppgave.metrics.Prometheus
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}
private val secureLog = KotlinLogging.logger("tjenestekall")

class OppgaveDataSink(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveClient,
    private val consumedEventName: String = Configuration.CONSUMED_EVENT_NAME,
    private val producedEventName: String = Configuration.PRODUCED_EVENT_NAME,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", consumedEventName) }
            validate { it.requireKey("fnrBruker", "joarkRef", "soknadId", "eventId") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.joarkRef get() = this["joarkRef"].textValue()
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
                        val soknadData = SoknadData(
                            fnrBruker = packet.fnrBruker,
                            joarkRef = packet.joarkRef,
                            soknadId = UUID.fromString(packet.soknadId),
                        )
                        log.info { "Arkivert søknad mottatt: ${soknadData.soknadId}" }
                        val oppgaveId = opprettOppgave(soknadData.fnrBruker, soknadData.joarkRef, soknadData.soknadId)
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

    private suspend fun opprettOppgave(fnrBruker: String, journalpostId: String, soknadId: UUID) =
        kotlin.runCatching {
            oppgaveClient.opprettOppgave(fnrBruker, journalpostId)
        }.onSuccess {
            log.info("Oppgave opprettet: $soknadId")
        }.onFailure {
            log.error(it) { "Feilet under opprettelse av oppgave: $soknadId" }
        }.getOrThrow()

    private fun CoroutineScope.forward(søknadData: SoknadData, joarkRef: String, context: MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(søknadData.fnrBruker, søknadData.toJson(joarkRef, producedEventName))
            Prometheus.oppgaveOpprettetCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    log.info("Oppgave opprettet for søknad: ${søknadData.soknadId}")
                    secureLog.info("Oppgave opprettet for søknad: ${søknadData.soknadId}, fnr: ${søknadData.fnrBruker})")
                }

                is CancellationException -> log.warn("Cancelled: ${it.message}")
                else -> {
                    log.error("Failed: ${it.message}. Soknad: ${søknadData.soknadId}")
                }
            }
        }
    }
}

internal data class SoknadData(
    val fnrBruker: String,
    val soknadId: UUID,
    val joarkRef: String,
) {
    internal fun toJson(oppgaveId: String, producedEventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["soknadId"] = this.soknadId
            it["eventName"] = producedEventName
            it["opprettet"] = LocalDateTime.now()
            it["fnrBruker"] = this.fnrBruker
            it["oppgaveId"] = oppgaveId
        }.toJson()
    }
}
