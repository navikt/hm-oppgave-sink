package no.nav.hjelpemidler.oppgave.service

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
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
import no.nav.hjelpemidler.oppgave.client.OppgaveClient
import no.nav.hjelpemidler.oppgave.client.models.OpprettOppgaveRequest
import no.nav.hjelpemidler.oppgave.domain.Sakstype
import no.nav.hjelpemidler.oppgave.metrics.Prometheus
import no.nav.hjelpemidler.oppgave.publish
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import no.nav.hjelpemidler.serialization.jackson.uuidValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Opprett oppgave for digital søknad som er overført fra Hotsak til Gosys.
 */
class OpprettOppgaveForOverføring(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveClient,
) : PacketListenerWithOnError {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "hm-opprettetMottattJournalpost") }
            validate {
                it.requireKey(
                    "fodselNrBruker",
                    "joarkRef",
                    "eventId",
                    "sakId",
                    "sakstype",
                )
                it.interestedIn(
                    "soknadId", // todo -> fjernes
                    "søknadId", // todo -> flyttes til requireKey
                    "journalpostId",
                    "fnrBruker",
                    "navIdent",
                    "valgteÅrsaker",
                    "enhet",
                    "begrunnelse",
                    "prioritet",
                )
            }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].uuidValue()

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

        val journalpost = jsonMapper.readValue<OpprettetMottattJournalpost>(packet.toJson())
        log.info {
            "Tilbakeført sak mottatt, sakId: ${journalpost.sakId}, sakstype: ${journalpost.sakstype}, søknadId: ${journalpost.søknadId}, journalpostId: ${journalpost.journalpostId}, prioritet: ${journalpost.prioritet}"
        }

        try {
            val oppgave = runBlocking(Dispatchers.IO) {
                oppgaveClient.opprettOppgave(lagOpprettJournalføringsoppgaveRequest(journalpost))
            }
            log.info { "Opprettet oppgave for journalpostId: ${journalpost.journalpostId} med oppgaveId: ${oppgave.id}" }

            context.publish(
                journalpost.fnrBruker,
                OpprettetJournalføringsoppgaveForTilbakeførtSakEvent(
                    søknadId = journalpost.søknadId,
                    oppgaveId = oppgave.id.toString(),
                    sakId = journalpost.sakId,
                    sakstype = journalpost.sakstype,
                    fnrBruker = journalpost.fnrBruker,
                    nyJournalpostId = journalpost.journalpostId,
                ),
            )
            Prometheus.oppgaveOpprettetCounter.increment()

            log.info {
                "Journalføringsoppgave opprettet for sak, sakId: ${journalpost.sakId}, journalpostId: ${journalpost.journalpostId}"
            }
        } catch (e: Exception) {
            log.error(e) {
                "Klarte ikke å opprette journalføringsoppgave for tilbakeført sak, sakId: ${journalpost.sakId}, journalpostId: ${journalpost.journalpostId}"
            }
            throw e
        }
    }

    private fun lagOpprettJournalføringsoppgaveRequest(journalpost: OpprettetMottattJournalpost): OpprettOppgaveRequest {
        val nå = LocalDate.now()
        val tema = "HJE"
        val oppgavetype = "JFR"
        val valgteÅrsaker = journalpost.valgteÅrsaker ?: emptySet()

        return when (val sakstype = journalpost.sakstype) {
            Sakstype.BARNEBRILLER -> {
                val behandlingstema = when {
                    "Behandlingsbriller/linser ordinære vilkår" in valgteÅrsaker -> "ab0427"
                    "Behandlingsbriller/linser særskilte vilkår" in valgteÅrsaker -> "ab0428"
                    else -> "ab0317" // "Briller/linser"
                }
                val beskrivelse = valgteÅrsaker.firstOrNull() ?: "Tilskudd ved kjøp av briller til barn"

                OpprettOppgaveRequest(
                    personident = journalpost.fnrBruker,
                    journalpostId = journalpost.journalpostId,
                    beskrivelse = when {
                        journalpost.begrunnelse.isNullOrBlank() -> beskrivelse
                        else -> "$beskrivelse: ${journalpost.begrunnelse}"
                    },
                    tema = tema,
                    oppgavetype = oppgavetype,
                    behandlingstema = behandlingstema,
                    aktivDato = nå,
                    fristFerdigstillelse = nå,
                    prioritet = OpprettOppgaveRequest.Prioritet.NORM,
                    opprettetAvEnhetsnr = journalpost.enhet,
                    tildeltEnhetsnr = journalpost.enhet,
                )
            }

            else -> {
                log.info { "lagOpprettJournalføringsoppgaveRequest sakstype: $sakstype" }

                val beskrivelse = sakstype.toBeskrivelse()

                val prioritet = journalpost.prioritet ?: OpprettOppgaveRequest.Prioritet.NORM
                OpprettOppgaveRequest(
                    personident = journalpost.fnrBruker,
                    journalpostId = journalpost.journalpostId,
                    beskrivelse = when {
                        valgteÅrsaker.isEmpty() -> beskrivelse
                        else -> "$beskrivelse. Overført av saksbehandler i Hotsak med begrunnelse: ${valgteÅrsaker.first()}"
                    },
                    tema = tema,
                    oppgavetype = oppgavetype,
                    behandlingstype = sakstype.toBehandlingstype(prioritet),
                    behandlingstema = sakstype.toBehandlingstema(prioritet),
                    aktivDato = nå,
                    fristFerdigstillelse = nå,
                    prioritet = prioritet,
                    // tilordnetRessurs = journalpost.navIdent, // fjernet fra spec
                )
            }
        }
    }

    private fun skipEvent(eventId: UUID): Boolean {
        val skip = setOf(
            "47376212-4289-4c0c-b6e6-417e4c989193",
        ).map(UUID::fromString)
        return eventId in skip
    }
}

data class OpprettetMottattJournalpost(
    @JsonAlias("joarkRef")
    val journalpostId: String,
    @JsonAlias("fodselNrBruker")
    val fnrBruker: String,
    @JsonAlias("soknadId")
    val søknadId: UUID,
    val sakId: String,
    val sakstype: Sakstype,
    val enhet: String,
    val navIdent: String?,
    val valgteÅrsaker: Set<String>? = null,
    val begrunnelse: String?,
    val prioritet: OpprettOppgaveRequest.Prioritet?,
)

@Suppress("unused")
data class OpprettetJournalføringsoppgaveForTilbakeførtSakEvent(
    @JsonProperty("soknadId")
    val søknadId: UUID,
    val oppgaveId: String,
    val sakId: String,
    val sakstype: Sakstype?,
    val fnrBruker: String,
    val nyJournalpostId: String,
    val eventId: UUID = UUID.randomUUID(),
    val opprettet: LocalDateTime = LocalDateTime.now(),
) {
    val eventName: String = "hm-opprettetJournalføringsoppgaveForTilbakeførtSak"
}
