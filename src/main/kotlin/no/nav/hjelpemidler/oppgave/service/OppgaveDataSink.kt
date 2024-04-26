package no.nav.hjelpemidler.oppgave.service

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.oppgave.Configuration
import no.nav.hjelpemidler.oppgave.client.OppgaveClient
import no.nav.hjelpemidler.oppgave.domain.Sakstype
import no.nav.hjelpemidler.oppgave.domain.SøknadData
import no.nav.hjelpemidler.oppgave.metrics.Prometheus
import no.nav.hjelpemidler.oppgave.serialization.publish
import no.nav.hjelpemidler.oppgave.serialization.uuidValue
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}
private val secureLog = KotlinLogging.logger("tjenestekall")

class OppgaveDataSink(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveClient,
    private val consumedEventName: String = Configuration.CONSUMED_EVENT_NAME,
) : PacketListenerWithOnError {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", consumedEventName) }
            validate { it.requireKey("fnrBruker", "joarkRef", "soknadId", "eventId", "sakstype") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].uuidValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.journalpostId get() = this["joarkRef"].textValue()
    private val JsonMessage.søknadId get() = this["soknadId"].uuidValue()

    private val JsonMessage.sakstype get() = Sakstype.valueOf(this["sakstype"].textValue())

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val eventId = packet.eventId
        if (skipEvent(eventId)) {
            log.info { "Hopper over event i skipList, eventId: $eventId" }
            return
        }

        try {
            val søknadId = packet.søknadId
            val sakstype = packet.sakstype
            val fnrBruker = packet.fnrBruker
            log.info { "Arkivert søknad mottatt, søknadId: $søknadId" }
            val oppgaveId = runBlocking(Dispatchers.IO) {
                opprettOppgave(
                    SøknadData(
                        søknadId = søknadId,
                        journalpostId = packet.journalpostId,
                        sakstype = sakstype,
                        fnrBruker = fnrBruker,
                    ),
                )
            }

            context.publish(
                fnrBruker,
                OppgaveOpprettetEvent(
                    søknadId = søknadId,
                    oppgaveId = oppgaveId,
                    sakstype = sakstype,
                    fnrBruker = fnrBruker,
                ),
            )

            Prometheus.oppgaveOpprettetCounter.inc()
            log.info("Oppgave opprettet for søknadId: $søknadId")
            secureLog.info("Oppgave opprettet for søknadId: $søknadId, fnrBruker: $fnrBruker")
        } catch (e: Exception) {
            throw RuntimeException("Håndtering av eventId: $eventId feilet", e)
        }
    }

    private fun skipEvent(eventId: UUID): Boolean {
        val skipList = setOf<UUID>()
        return eventId in skipList
    }

    private suspend fun opprettOppgave(søknadData: SøknadData): String =
        runCatching { oppgaveClient.opprettOppgave(søknadData) }
            .onSuccess { log.info("Oppgave opprettet: ${søknadData.søknadId}") }
            .onFailure { log.error(it) { "Feilet under opprettelse av oppgave for søknadId: ${søknadData.søknadId}" } }
            .getOrThrow()
}

@Suppress("unused")
data class OppgaveOpprettetEvent(
    @JsonProperty("soknadId")
    val søknadId: UUID,
    val oppgaveId: String,
    val sakstype: Sakstype,
    val fnrBruker: String,
) {
    val eventId: UUID = UUID.randomUUID()
    val eventName: String = Configuration.PRODUCED_EVENT_NAME
    val opprettet: LocalDateTime = LocalDateTime.now()
}
