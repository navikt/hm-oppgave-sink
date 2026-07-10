package no.nav.hjelpemidler.oppgave.service

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.kafka.KafkaMessage
import no.nav.hjelpemidler.logging.teamInfo
import no.nav.hjelpemidler.oppgave.client.OppgaveClient
import no.nav.hjelpemidler.oppgave.client.models.OpprettOppgaveRequest
import no.nav.hjelpemidler.oppgave.domain.Delbestilling
import no.nav.hjelpemidler.oppgave.domain.Sakstype
import no.nav.hjelpemidler.oppgave.metrics.Prometheus
import no.nav.hjelpemidler.rapids_and_rivers.publish
import no.nav.hjelpemidler.serialization.jackson.uuidValue
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Opprett oppgave for digital delbestillings-søknad som må behandles i Gosys.
 *
 * @see <a href="https://github.com/navikt/hm-joark-sink/blob/main/src/main/kotlin/no/nav/hjelpemidler/joark/service/OpprettManuellDelbestilling.kt">OpprettManuellDelbestilling</a>
 */
class OpprettOppgaveForDelbestilling(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveClient,
) : PacketListenerWithOnError {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "hm-DelbestillingArkivert") }
            validate {
                it.requireKey(
                    "eventId",
                    "saksnummer",
                    "joarkRef",
                    "mottattTidspunkt",
                    "joarkRef",
                    "dokumentTittel",
                    "eksternReferanseId",
                    "mottattTidspunkt",
                    "eventId"
                )
            }
        }.register(this)
    }

    private val JsonMessage.saksnummer get() = this["saksnummer"].longValue()
    private val JsonMessage.brukersFnr get() = this["brukersFnr"].stringValue()
    private val JsonMessage.joarkRef get() = this["joarkRef"].stringValue()
    private val JsonMessage.eventId get() = this["eventId"].uuidValue()

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val eventId = packet.eventId

        try {
            val saksnummer = packet.saksnummer
            val journalpostId = packet.joarkRef
            val sakstype = Sakstype.DELBESTILLING
            val fnrBruker = packet.brukersFnr

            log.info { "Arkivert delbestilling mottatt, Saksnr: $saksnummer, journalpostId: $journalpostId, sakstype: $sakstype" }

            val oppgaveId = opprettOppgave(
                Delbestilling(
                    saksnummer = saksnummer,
                    journalpostId = journalpostId,
                    sakstype = sakstype,
                    fnrBruker = fnrBruker,
                    prioritet = OpprettOppgaveRequest.Prioritet.NORM,
                ),
            )

            context.publish(
                key = fnrBruker,
                message = DelbestillingsOppgaveOpprettetEvent(
                    saksnummer = saksnummer,
                    oppgaveId = oppgaveId,
                    brukersFnr = fnrBruker,
                    journalpostId,
                ),
            )
        } catch (e: Exception) {
            throw RuntimeException("Håndtering av eventId: $eventId feilet", e)
        }
    }

    private fun opprettOppgave(delbestilling: Delbestilling): String {
        val saksnummer = delbestilling.saksnummer

        return runCatching {
            runBlocking(Dispatchers.IO) { oppgaveClient.opprettOppgave(delbestilling) }
        }
            .onSuccess { oppgaveId ->
                log.info { "Delbestillingsppgave opprettet, saksnummer: $saksnummer, oppgaveId: $oppgaveId" }
                log.teamInfo { "Oppgave opprettet, søknadId: $saksnummer, oppgaveId: $oppgaveId, fnrBruker: ${delbestilling.fnrBruker}" }

                Prometheus.oppgaveOpprettetCounter.increment()
            }
            .onFailure { log.error(it) { "Feil under opprettelse av delbestillingsoppgave for saksnummer: $saksnummer" } }
            .getOrThrow()
    }
}

@Suppress("unused")
data class DelbestillingsOppgaveOpprettetEvent(
    val saksnummer: Long,
    val oppgaveId: String,
    val brukersFnr: String,
    val joarkRef: String,
) : KafkaMessage {
    override val eventName: String = "hm-delbestillingsoppgave-opprettet"
    override val eventId: UUID = UUID.randomUUID()
}
