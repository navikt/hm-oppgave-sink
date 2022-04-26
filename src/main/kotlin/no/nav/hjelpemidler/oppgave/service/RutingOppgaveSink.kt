package no.nav.hjelpemidler.oppgave.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.oppgave.oppgave.OppgaveClient
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private val mapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

private val logg = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class RutingOppgaveSink(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveClient,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-ruting-oppgave") }
            validate { it.requireKey("eventId", "opprettet", "aktoerId", "journalpostId", "tema", "oppgavetype", "aktivDato", "prioritet", "opprettetAvEnhetsnr", "fristFerdigstillelse", "beskrivelse") }
            validate { it.interestedIn("behandlingstema", "behandlingtype", "tildeltEnhetsnr") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (skipEvent(UUID.fromString(packet.eventId))) {
            logg.info { "Hopper over event i skip-list: ${packet.eventId}" }
            return
        }

        val oppgave: RutingOppgave = mapper.readValue(packet.toJson())

        try {
            logg.info("Ruting oppgave mottatt: ${mapper.writeValueAsString(oppgave)}")
            // val oppgaveId = opprettOppgave(aktorId, soknadData.joarkRef, soknadData.soknadId)
        } catch (e: Exception) {
            logg.error(e) { "Håndtering av ruting oppgave feilet (eventID=${packet.eventId})" }
            throw e
        }
    }

    private fun skipEvent(eventId: UUID): Boolean {
        val skipList = listOf<UUID>()
        return skipList.any { it == eventId }
    }
}

data class RutingOppgave(
    val eventId: UUID,
    val eventName: String,
    val opprettet: LocalDateTime,

    val aktoerId: String,
    val journalpostId: Int,
    val tema: String,
    val behandlingstema: String?,
    val behandlingtype: String?,
    val oppgavetype: String,
    val aktivDato: LocalDate,
    val prioritet: String,
    val opprettetAvEnhetsnr: String,
    val fristFerdigstillelse: LocalDate,
    val tildeltEnhetsnr: String?,
    val beskrivelse: String,
)
