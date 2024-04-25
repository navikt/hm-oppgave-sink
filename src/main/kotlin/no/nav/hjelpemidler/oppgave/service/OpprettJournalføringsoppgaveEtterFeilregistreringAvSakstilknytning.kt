package no.nav.hjelpemidler.oppgave.service

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.oppgave.client.OppgaveClient
import no.nav.hjelpemidler.oppgave.client.models.OpprettOppgaveRequest
import no.nav.hjelpemidler.oppgave.domain.Sakstype
import no.nav.hjelpemidler.oppgave.jsonMapper
import no.nav.hjelpemidler.oppgave.metrics.Prometheus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

class OpprettJournalføringsoppgaveEtterFeilregistreringAvSakstilknytning(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveClient,
) : PacketListenerWithOnError {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-opprettetMottattJournalpost") }
            validate {
                it.requireKey(
                    "fodselNrBruker",
                    "joarkRef",
                    "eventId",
                    "sakId",
                    "soknadId",
                )
                it.interestedIn(
                    "journalpostId",
                    "fnrBruker",
                    "sakstype",
                    "navIdent",
                    "valgteÅrsaker",
                    "enhet",
                    "begrunnelse",
                )
            }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()

    data class OpprettetMottattJournalpost(
        @JsonAlias("joarkRef")
        val journalpostId: String,
        @JsonAlias("fodselNrBruker")
        val fnrBruker: String,
        @JsonAlias("soknadId")
        val søknadId: UUID,
        val sakId: String,
        val sakstype: Sakstype?,
        val enhet: String,
        val navIdent: String?,
        val valgteÅrsaker: Set<String>? = null,
        val begrunnelse: String?,
    )

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        if (skipEvent(packet.eventId)) {
            log.info { "Hopper over event i skipList, eventId: ${packet.eventId}" }
            return
        }
        val journalpost = jsonMapper.readValue<OpprettetMottattJournalpost>(packet.toJson())
        log.info {
            "Tilbakeført sak mottatt, sakId: ${journalpost.sakId}, sakstype: ${journalpost.sakstype}, søknadId: ${journalpost.søknadId}, journalpostId: ${journalpost.journalpostId}"
        }
        try {
            val oppgave = runBlocking(Dispatchers.IO) {
                oppgaveClient.opprettOppgave(lagOpprettJournalføringsoppgaveRequest(journalpost))
            }
            log.info("Oppgave for journalpostId: ${journalpost.journalpostId} opprettet med oppgaveId: ${oppgave.id}")

            val data = OpprettJournalføringsoppgaveEtterFeilregistreringOppgaveData(
                fnrBruker = journalpost.fnrBruker,
                sakId = journalpost.sakId,
                sakstype = journalpost.sakstype,
                søknadId = journalpost.søknadId,
                nyJournalpostId = journalpost.journalpostId,
            )
            context.publish(
                data.fnrBruker,
                data.toJson(
                    oppgave.id.toString(),
                    "hm-opprettetJournalføringsoppgaveForTilbakeførtSak",
                ),
            )
            Prometheus.oppgaveOpprettetCounter.inc()

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
        return when (val sakstype = journalpost.sakstype) {
            Sakstype.BARNEBRILLER -> {
                val valgteÅrsaker = journalpost.valgteÅrsaker ?: emptySet()
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
                    tilordnetRessurs = journalpost.navIdent,
                    opprettetAvEnhetsnr = journalpost.enhet,
                    tildeltEnhetsnr = journalpost.enhet,
                )
            }

            else -> {
                log.info { "lagOpprettJournalføringsoppgaveRequest sakstype: $sakstype" }
                if (sakstype == null) {
                    log.info { "lagOpprettJournalføringsoppgaveRequest sakstype er null" }
                }

                OpprettOppgaveRequest(
                    personident = journalpost.fnrBruker,
                    journalpostId = journalpost.journalpostId,
                    beskrivelse = "Digital søknad om hjelpemidler",
                    tema = tema,
                    oppgavetype = oppgavetype,
                    behandlingstype = "ae0227",
                    aktivDato = nå,
                    fristFerdigstillelse = nå,
                    prioritet = OpprettOppgaveRequest.Prioritet.NORM,
                    tilordnetRessurs = journalpost.navIdent,
                )
            }
        }
    }

    private fun skipEvent(eventId: String): Boolean {
        val skip = setOf("47376212-4289-4c0c-b6e6-417e4c989193")
        return eventId in skip
    }
}

data class OpprettJournalføringsoppgaveEtterFeilregistreringOppgaveData(
    val fnrBruker: String,
    val sakId: String,
    val sakstype: Sakstype?,
    val søknadId: UUID,
    val nyJournalpostId: String,
) {
    fun toJson(
        oppgaveId: String,
        producedEventName: String,
    ): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventName"] = producedEventName
            it["opprettet"] = LocalDateTime.now()
            it["fnrBruker"] = this.fnrBruker
            it["oppgaveId"] = oppgaveId
            it["sakId"] = sakId
            if (sakstype != null) {
                it["sakstype"] = sakstype.toString()
            }
            it["nyJournalpostId"] = nyJournalpostId
            it["soknadId"] = this.søknadId
        }.toJson()
    }
}
