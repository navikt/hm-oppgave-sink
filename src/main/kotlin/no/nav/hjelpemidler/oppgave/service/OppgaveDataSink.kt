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
import no.nav.hjelpemidler.oppgave.domain.Sakstype
import no.nav.hjelpemidler.oppgave.domain.SoknadData
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
            validate { it.requireKey("fnrBruker", "joarkRef", "soknadId", "eventId", "sakstype") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.joarkRef get() = this["joarkRef"].textValue()
    private val JsonMessage.soknadId get() = this["soknadId"].textValue()

    private val JsonMessage.sakstype get() = Sakstype.valueOf(this["sakstype"].textValue())

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    if (skipEvent(UUID.fromString(packet.eventId))) {
                        log.info { "Hopper over event i skip-list: ${packet.eventId}" }
                        return@launch
                    }
                    try {
                        val soknadData =
                            SoknadData(
                                fnrBruker = packet.fnrBruker,
                                joarkRef = packet.joarkRef,
                                soknadId = UUID.fromString(packet.soknadId),
                                sakstype = packet.sakstype,
                            )
                        log.info { "Arkivert søknad mottatt: ${soknadData.soknadId}" }
                        val oppgaveId = opprettOppgave(soknadData)
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

    private suspend fun opprettOppgave(soknadData: SoknadData) =
        kotlin.runCatching {
            oppgaveClient.opprettOppgave(soknadData)
        }.onSuccess {
            log.info("Oppgave opprettet: ${soknadData.soknadId}")
        }.onFailure {
            log.error(it) { "Feilet under opprettelse av oppgave: ${soknadData.soknadId}" }
        }.getOrThrow()

    private fun CoroutineScope.forward(
        søknadData: SoknadData,
        joarkRef: String,
        context: MessageContext,
    ) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(søknadData.fnrBruker, toJson(søknadData, joarkRef, producedEventName))
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

internal fun toJson(
    soknadData: SoknadData,
    oppgaveId: String,
    producedEventName: String,
): String {
    return JsonMessage("{}", MessageProblems("")).also {
        it["soknadId"] = soknadData.soknadId
        it["eventName"] = producedEventName
        it["opprettet"] = LocalDateTime.now()
        it["fnrBruker"] = soknadData.fnrBruker
        it["oppgaveId"] = oppgaveId
        it["sakstype"] = soknadData.sakstype
    }.toJson()
}
