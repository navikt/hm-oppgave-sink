package no.nav.hjelpemidler.oppgave.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.oppgave.client.OppgaveClient
import no.nav.hjelpemidler.oppgave.client.models.OpprettOppgaveRequest
import no.nav.hjelpemidler.oppgave.metrics.MetricsProducer
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private val mapper =
    jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

private val log = KotlinLogging.logger {}
private val secureLog = KotlinLogging.logger("tjenestekall")

internal class RutingOppgaveSink(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveClient,
) : PacketListenerWithOnError {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-ruting-oppgave") }
            validate {
                it.requireKey(
                    "eventId",
                    "opprettet",
                    "journalpostId",
                    "tema",
                    "oppgavetype",
                    "aktivDato",
                    "prioritet",
                    "opprettetAvEnhetsnr",
                    "fristFerdigstillelse",
                    "beskrivelse",
                )
            }
            validate { it.interestedIn("aktoerId", "behandlingstema", "behandlingtype", "tildeltEnhetsnr") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue().let(UUID::fromString)

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val metrics = MetricsProducer(context)

        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    if (skipEvent(packet.eventId)) {
                        log.info { "Hopper over event i skip-list: ${packet.eventId}" }
                        return@launch
                    }

                    metrics.rutingOppgaveForsøktHåndtert(packet["oppgavetype"].asText())

                    val oppgave: RutingOppgave = mapper.readValue(packet.toJson())
                    try {
                        log.info("Ruting oppgave mottatt: ${mapper.writeValueAsString(oppgave)}")

                        // Sjekk om det allerede finnes en oppgave for denne journalposten, da kan vi nemlig slutte prosesseringen tidlig.
                        if (oppgaveClient.harAlleredeOppgaveForJournalpost(oppgave.journalpostId)) {
                            log.info(
                                "Ruting oppgave ble skippet da det allerede finnes en oppgave for journalpostId: ${oppgave.journalpostId}",
                            )
                            metrics.rutingOppgaveEksisterteAllerede(oppgave.oppgavetype)
                            return@launch
                        }

                        log.info("Ruting oppgave kan opprettes, den finnes ikke fra før!")

                        // Opprett oppgave for journalpost
                        opprettOppgave(oppgave)
                        metrics.rutingOppgaveOpprettet(oppgave.oppgavetype)
                    } catch (e: Exception) {
                        log.error(e) { "Håndtering av ruting oppgave feilet (eventId: ${packet.eventId})" }
                        metrics.rutingOppgaveException(oppgave.oppgavetype)
                        throw e
                    }
                }
            }
        }
    }

    private suspend fun opprettOppgave(oppgave: RutingOppgave) =
        kotlin.runCatching {
            oppgaveClient.opprettOppgaveBasertPåRutingOppgave(oppgave)
        }.onSuccess {
            log.info("Journalføringsoppgave opprettet for ruting-oppgave: journalpostId: ${oppgave.journalpostId}, oppgaveId: $it")
        }.onFailure {
            log.error(it) {
                "Feilet under opprettelse av journalføringsoppgave for ruting-oppgave, journalpostId: ${oppgave.journalpostId}" +
                    " tildelt enhet: ${oppgave.tildeltEnhetsnr} opprettet av enhet: ${oppgave.opprettetAvEnhetsnr}"
            }
        }.getOrThrow()

    private fun skipEvent(eventId: UUID): Boolean {
        val skipList =
            setOf(
                UUID.fromString("47376212-4289-4c0c-b6e6-417e4c989193"),
                UUID.fromString("d0a68746-4804-44f1-931f-0b52ec9f9c95"),
            )
        return eventId in skipList
    }
}

data class RutingOppgave(
    val eventId: UUID,
    val eventName: String,
    val opprettet: LocalDateTime,
    val aktoerId: String?,
    val orgnr: String?,
    val journalpostId: String,
    val tema: String,
    val behandlingstema: String?,
    val behandlingtype: String?,
    val oppgavetype: String,
    val aktivDato: LocalDate,
    val prioritet: OpprettOppgaveRequest.Prioritet,
    val opprettetAvEnhetsnr: String,
    val fristFerdigstillelse: LocalDate,
    val tildeltEnhetsnr: String?,
    val beskrivelse: String,
)
