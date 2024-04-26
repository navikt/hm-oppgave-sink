package no.nav.hjelpemidler.oppgave.client

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import mu.KotlinLogging
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.bearerAuth
import no.nav.hjelpemidler.oppgave.client.models.Oppgave
import no.nav.hjelpemidler.oppgave.client.models.OpprettOppgaveRequest
import no.nav.hjelpemidler.oppgave.client.models.SokOppgaverResponse
import no.nav.hjelpemidler.oppgave.domain.Sakstype
import no.nav.hjelpemidler.oppgave.domain.Søknad
import no.nav.hjelpemidler.oppgave.service.RutingOppgave
import java.time.LocalDate

private val log = KotlinLogging.logger {}

class OppgaveClient(
    private val baseUrl: String,
    private val scope: String,
    private val azureAdClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val client = createHttpClient(engine) {
        expectSuccess = true
        defaultRequest {
            correlationId()
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun harOppgaveForJournalpost(journalpostId: String): Boolean {
        val tokenSet = azureAdClient.grant(scope)
        val response = client.get(baseUrl) {
            bearerAuth(tokenSet)
            parameter("journalpostId", journalpostId)
            parameter("statuskategori", "AAPEN")
            parameter("oppgavetype", "JFR")
            parameter("oppgavetype", "FDR")
        }

        val sokOppgaverResponse = response.body<SokOppgaverResponse>()
        val antallTreffTotalt = sokOppgaverResponse.antallTreffTotalt

        return antallTreffTotalt != null && antallTreffTotalt > 0
    }

    suspend fun opprettOppgaveBasertPåRutingOppgave(rutingOppgave: RutingOppgave): String {
        log.info("Oppretter gosys-oppgave basert på ruting-oppgave, journalpostId: ${rutingOppgave.journalpostId}")

        val tildeltEnhet = when (rutingOppgave.tildeltEnhetsnr) {
            in videresendingEnheter -> {
                val nyEnhet = videresendingEnheter[rutingOppgave.tildeltEnhetsnr]
                log.warn {
                    "Mappet om nedlagt/sammenslått enhet: ${rutingOppgave.tildeltEnhetsnr} til ny enhet: $nyEnhet for journalpostId: ${rutingOppgave.journalpostId}"
                }
                nyEnhet
            }

            else -> rutingOppgave.tildeltEnhetsnr
        }

        return opprettOppgave(
            OpprettOppgaveRequest(
                personident = rutingOppgave.aktørId,
                orgnr = rutingOppgave.orgnr,
                journalpostId = rutingOppgave.journalpostId,
                beskrivelse = rutingOppgave.beskrivelse,
                tema = rutingOppgave.tema,
                oppgavetype = rutingOppgave.oppgavetype,
                aktivDato = rutingOppgave.aktivDato,
                fristFerdigstillelse = rutingOppgave.fristFerdigstillelse,
                prioritet = rutingOppgave.prioritet,
                opprettetAvEnhetsnr = rutingOppgave.opprettetAvEnhetsnr,
                tildeltEnhetsnr = tildeltEnhet,
                behandlingstema = rutingOppgave.behandlingstema,
                behandlingstype = rutingOppgave.behandlingstype,
            ),
        ).id.toString()
    }

    suspend fun opprettOppgave(søknad: Søknad): String {
        val nå = LocalDate.now()
        return opprettOppgave(
            OpprettOppgaveRequest(
                personident = søknad.fnrBruker,
                journalpostId = søknad.journalpostId,
                beskrivelse = søknad.sakstype.beskrivelse,
                tema = "HJE",
                oppgavetype = "JFR",
                behandlingstype = søknad.sakstype.behandlingstype,
                aktivDato = nå,
                fristFerdigstillelse = nå,
                prioritet = OpprettOppgaveRequest.Prioritet.NORM,
            ),
        ).id.toString()
    }

    suspend fun opprettOppgave(request: OpprettOppgaveRequest): Oppgave {
        log.info { "Oppretter oppgave, journalpostId: ${request.journalpostId}" }

        val tokenSet = azureAdClient.grant(scope)
        val response = client.post(baseUrl) {
            bearerAuth(tokenSet)
            setBody(request)
        }

        return response.body<Oppgave>()
    }
}

private val Sakstype.beskrivelse
    get() = when (this) {
        Sakstype.BYTTE, Sakstype.BRUKERPASSBYTTE -> "Digitalt bytte av hjelpemidler"
        else -> "Digital søknad om hjelpemidler"
    }

private val Sakstype.behandlingstype
    get() = when (this) {
        Sakstype.BYTTE, Sakstype.BRUKERPASSBYTTE -> "ae0273"
        else -> "ae0227"
    }

/**
 * Nedlagte/sammenslåtte enheter som skal sendes til ny enhet. Kan skje av og til f.eks. pga. at avsender har brukt en gammel forside som de hadde liggende
 */
private val videresendingEnheter = mapOf(
    "4708" to "4707",
    "4709" to "4710",
    "4717" to "4716",
    "4720" to "4719",
    "1190" to null, // vi lar arbeidsfordelingen i NORG ta seg av denne
)
