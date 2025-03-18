package no.nav.hjelpemidler.oppgave.service

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.logging.secureLog
import no.nav.hjelpemidler.oppgave.client.OppgaveClient
import no.nav.hjelpemidler.oppgave.client.models.OpprettOppgaveRequest
import no.nav.hjelpemidler.oppgave.metrics.MetricsProducer
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import no.nav.hjelpemidler.serialization.jackson.uuidValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Opprett oppgave for papirsøknad mottatt i hm-joark-listener.
 *
 * @see <a href="https://github.com/navikt/hm-joark-listener/blob/main/src/main/kotlin/no/nav/hjelpemidler/joark/JournalpostMottattRiver.kt">JournalpostMottattRiver</a>
 */
class OpprettOppgaveForPapirsøknad(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveClient,
) : PacketListenerWithOnError {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "hm-ruting-oppgave") }
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
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val metrics = MetricsProducer(context)

        if (skipEvent(packet.eventId)) {
            log.info { "Hopper over event i skipList, eventId: ${packet.eventId}" }
            return
        }

        metrics.rutingOppgaveForsøktHåndtert(packet["oppgavetype"].asText())

        val rutingOppgave: RutingOppgave = jsonMapper.readValue(packet.toJson())
        try {
            secureLog.info { "Ruting-oppgave mottatt: '${jsonMapper.writeValueAsString(rutingOppgave)}'" }

            // Sjekk om det allerede finnes en oppgave for denne journalposten, da kan vi nemlig slutte prosesseringen tidlig.
            val harAlleredeOppgaveForJournalpost = runBlocking(Dispatchers.IO) {
                oppgaveClient.harOppgaveForJournalpost(rutingOppgave.journalpostId)
            }
            if (harAlleredeOppgaveForJournalpost) {
                log.info { "Ruting-oppgave ble skippet da det allerede finnes en oppgave for journalpostId: ${rutingOppgave.journalpostId}" }
                metrics.rutingOppgaveEksisterteAllerede(rutingOppgave.oppgavetype)
                return
            }

            log.info { "Ruting-oppgave kan opprettes, den finnes ikke fra før, journalpostId: ${rutingOppgave.journalpostId}" }

            // Opprett oppgave for journalpost
            opprettOppgave(rutingOppgave)
            metrics.rutingOppgaveOpprettet(rutingOppgave.oppgavetype)
        } catch (e: Exception) {
            log.error(e) { "Håndtering av ruting-oppgave feilet, eventId: ${packet.eventId}, journalpostId: ${rutingOppgave.journalpostId}" }
            metrics.rutingOppgaveException(rutingOppgave.oppgavetype)
            throw e
        }
    }

    private fun opprettOppgave(oppgave: RutingOppgave) = runCatching { runBlocking(Dispatchers.IO) { oppgaveClient.opprettOppgave(oppgave) } }
        .onSuccess { oppgaveId -> log.info { "Journalføringsoppgave opprettet for ruting-oppgave, journalpostId: ${oppgave.journalpostId}, oppgaveId: $oppgaveId" } }
        .onFailure { log.error(it) { "Feil under opprettelse av journalføringsoppgave for ruting-oppgave, journalpostId: ${oppgave.journalpostId}, tildeltEnhetsnr: ${oppgave.tildeltEnhetsnr}, opprettetAvEnhetsnr: ${oppgave.opprettetAvEnhetsnr}" } }
        .getOrThrow()

    private fun skipEvent(eventId: UUID): Boolean {
        val skipList = setOf(
            "47376212-4289-4c0c-b6e6-417e4c989193",
            "d0a68746-4804-44f1-931f-0b52ec9f9c95",
            "6b8d59cd-3b7c-48a4-86bc-fea690758f85",
            "14f09097-d2bf-4d4e-97cc-709a3854ec98",
            "5fad9d57-b18a-4e3e-a182-5aa309d33074",
        ).map(UUID::fromString)
        return eventId in skipList
    }
}

data class RutingOppgave(
    val eventId: UUID,
    val eventName: String,
    val opprettet: LocalDateTime,
    @JsonAlias("aktoerId")
    val aktørId: String?,
    val orgnr: String?,
    val journalpostId: String,
    val tema: String,
    val behandlingstema: String?,
    @JsonAlias("behandlingtype")
    val behandlingstype: String?,
    val oppgavetype: String,
    val aktivDato: LocalDate,
    val prioritet: OpprettOppgaveRequest.Prioritet,
    val opprettetAvEnhetsnr: String,
    val fristFerdigstillelse: LocalDate,
    val tildeltEnhetsnr: String?,
    val beskrivelse: String,
)
