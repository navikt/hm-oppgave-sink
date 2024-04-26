package no.nav.hjelpemidler.oppgave.service

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.oppgave.client.OppgaveClient
import no.nav.hjelpemidler.oppgave.client.models.OpprettOppgaveRequest
import no.nav.hjelpemidler.oppgave.metrics.MetricsProducer
import no.nav.hjelpemidler.oppgave.serialization.jsonMapper
import no.nav.hjelpemidler.oppgave.serialization.uuidValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

class RutingOppgaveSink(
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

    private val JsonMessage.eventId get() = this["eventId"].uuidValue()

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val metrics = MetricsProducer(context)

        if (skipEvent(packet.eventId)) {
            log.info { "Hopper over event i skipList, eventId: ${packet.eventId}" }
            return
        }

        metrics.rutingOppgaveForsøktHåndtert(packet["oppgavetype"].asText())

        val oppgave: RutingOppgave = jsonMapper.readValue(packet.toJson())
        try {
            log.info("Ruting-oppgave mottatt: '${jsonMapper.writeValueAsString(oppgave)}'")

            // Sjekk om det allerede finnes en oppgave for denne journalposten, da kan vi nemlig slutte prosesseringen tidlig.
            val harAlleredeOppgaveForJournalpost = runBlocking(Dispatchers.IO) {
                oppgaveClient.harAlleredeOppgaveForJournalpost(oppgave.journalpostId)
            }
            if (harAlleredeOppgaveForJournalpost) {
                log.info(
                    "Ruting-oppgave ble skippet da det allerede finnes en oppgave for journalpostId: ${oppgave.journalpostId}",
                )
                metrics.rutingOppgaveEksisterteAllerede(oppgave.oppgavetype)
                return
            }

            log.info("Ruting-oppgave kan opprettes, den finnes ikke fra før, journalpostId: ${oppgave.journalpostId}")

            // Opprett oppgave for journalpost
            opprettOppgave(oppgave)
            metrics.rutingOppgaveOpprettet(oppgave.oppgavetype)
        } catch (e: Exception) {
            log.error(e) { "Håndtering av ruting-oppgave feilet, eventId: ${packet.eventId}, journalpostId: ${oppgave.journalpostId}" }
            metrics.rutingOppgaveException(oppgave.oppgavetype)
            throw e
        }
    }

    private fun opprettOppgave(oppgave: RutingOppgave) =
        runCatching { runBlocking(Dispatchers.IO) { oppgaveClient.opprettOppgaveBasertPåRutingOppgave(oppgave) } }
            .onSuccess { log.info("Journalføringsoppgave opprettet for ruting-oppgave, journalpostId: ${oppgave.journalpostId}, oppgaveId: $it") }
            .onFailure { log.error(it) { "Feilet under opprettelse av journalføringsoppgave for ruting-oppgave, journalpostId: ${oppgave.journalpostId}, tildeltEnhetsnr: ${oppgave.tildeltEnhetsnr}, opprettetAvEnhetsnr: ${oppgave.opprettetAvEnhetsnr}" } }
            .getOrThrow()

    private fun skipEvent(eventId: UUID): Boolean {
        val skipList = setOf(
            "47376212-4289-4c0c-b6e6-417e4c989193",
            "d0a68746-4804-44f1-931f-0b52ec9f9c95",
            "6b8d59cd-3b7c-48a4-86bc-fea690758f85",
            "14f09097-d2bf-4d4e-97cc-709a3854ec98",
        ).map(UUID::fromString)
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
