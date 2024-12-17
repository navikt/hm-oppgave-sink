package no.nav.hjelpemidler.oppgave.service

import com.fasterxml.jackson.annotation.JsonProperty
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
import no.nav.hjelpemidler.oppgave.Configuration
import no.nav.hjelpemidler.oppgave.client.OppgaveClient
import no.nav.hjelpemidler.oppgave.client.models.OpprettOppgaveRequest
import no.nav.hjelpemidler.oppgave.domain.Sakstype
import no.nav.hjelpemidler.oppgave.domain.Søknad
import no.nav.hjelpemidler.oppgave.metrics.Prometheus
import no.nav.hjelpemidler.oppgave.serialization.publish
import no.nav.hjelpemidler.serialization.jackson.uuidValue
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Opprett oppgave for digital søknad som må behandles i Gosys.
 *
 * @see <a href="https://github.com/navikt/hm-joark-sink/blob/main/src/main/kotlin/no/nav/hjelpemidler/joark/service/OpprettJournalpostS%C3%B8knadFordeltGammelFlyt.kt">OpprettJournalpostSøknadFordeltGammelFlyt</a>
 */
class OpprettOppgaveForDigitalSøknad(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveClient,
    private val consumedEventName: String = Configuration.CONSUMED_EVENT_NAME,
) : PacketListenerWithOnError {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", consumedEventName) }
            validate { it.requireKey("fnrBruker", "joarkRef", "soknadId", "eventId", "sakstype", "erHast") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].uuidValue()
    private val JsonMessage.søknadId get() = this["soknadId"].uuidValue()
    private val JsonMessage.journalpostId get() = this["joarkRef"].textValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()

    private val JsonMessage.sakstype get() = Sakstype.valueOf(this["sakstype"].textValue())

    private val JsonMessage.erHast get() = this["erHast"].booleanValue()

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val eventId = packet.eventId
        if (skipEvent(eventId)) {
            log.info { "Hopper over event i skipList, eventId: $eventId" }
            return
        }

        try {
            val søknadId = packet.søknadId
            val journalpostId = packet.journalpostId
            val sakstype = packet.sakstype
            val fnrBruker = packet.fnrBruker
            val erHast = packet.erHast

            log.info { "Arkivert søknad mottatt, søknadId: $søknadId, journalpostId: $journalpostId, sakstype: $sakstype, erHast: $erHast" }

            val oppgaveId = opprettOppgave(
                Søknad(
                    søknadId = søknadId,
                    journalpostId = journalpostId,
                    sakstype = sakstype,
                    fnrBruker = fnrBruker,
                    prioritet = if (erHast) OpprettOppgaveRequest.Prioritet.HOY else OpprettOppgaveRequest.Prioritet.NORM,
                ),
            )

            context.publish(
                fnrBruker,
                OppgaveOpprettetEvent(
                    søknadId = søknadId,
                    oppgaveId = oppgaveId,
                    sakstype = sakstype,
                    fnrBruker = fnrBruker,
                ),
            )
        } catch (e: Exception) {
            throw RuntimeException("Håndtering av eventId: $eventId feilet", e)
        }
    }

    private fun skipEvent(eventId: UUID): Boolean {
        val skipList = setOf<UUID>(
            UUID.fromString("d38e62d6-a804-4f22-8975-e8b16b4eae3e"),
        )
        return eventId in skipList
    }

    private fun opprettOppgave(søknad: Søknad): String {
        val søknadId = søknad.søknadId

        return runCatching {
            runBlocking(Dispatchers.IO) { oppgaveClient.opprettOppgave(søknad) }
        }
            .onSuccess { oppgaveId ->
                log.info { "Oppgave opprettet, søknadId: $søknadId, oppgaveId: $oppgaveId" }
                secureLog.info { "Oppgave opprettet, søknadId: $søknadId, oppgaveId: $oppgaveId, fnrBruker: ${søknad.fnrBruker}" }

                Prometheus.oppgaveOpprettetCounter.increment()
            }
            .onFailure { log.error(it) { "Feil under opprettelse av oppgave for søknadId: $søknadId" } }
            .getOrThrow()
    }
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
