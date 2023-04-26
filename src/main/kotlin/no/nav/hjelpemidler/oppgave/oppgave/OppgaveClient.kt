package no.nav.hjelpemidler.oppgave.oppgave

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import mu.KotlinLogging
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.bearerAuth
import no.nav.hjelpemidler.oppgave.oppgave.model.OppgaveRequest
import no.nav.hjelpemidler.oppgave.oppgave.model.OppgaveRequestRutingOppgave
import no.nav.hjelpemidler.oppgave.service.RutingOppgave
import java.time.LocalDate
import java.util.UUID

private val log = KotlinLogging.logger {}

class OppgaveClient(
    private val baseUrl: String,
    private val scope: String,
    private val azureAdClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    companion object {
        const val BEHANDLINGSTYPE = "ae0227" // Digital søknad
        const val OPPGAVETYPE_JRF = "JFR"
        const val OPPGAVE_PRIORITET_NORM = "NORM"
        const val TEMA = "HJE"
        const val TEMA_GRUPPE = "HJLPM"
        const val BESKRIVELSE_OPPGAVE = "Digital søknad om hjelpemidler"
    }

    private val client = createHttpClient(engine) {
        expectSuccess = false
        defaultRequest {
            header("X-Correlation-ID", UUID.randomUUID().toString())
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun harAlleredeOppgaveForJournalpost(journalpostId: Int): Boolean {
        val tokenSet = azureAdClient.grant(scope)
        val response = client.get(baseUrl) {
            bearerAuth(tokenSet)
            parameter("journalpostId", journalpostId)
            parameter("statuskategori", "AAPEN")
            parameter("oppgavetype", "JFR")
            parameter("oppgavetype", "FDR")
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                response.body<HentOppgaverResponse>().antallTreffTotalt > 0
            }

            else -> {
                val body = runCatching { response.bodyAsText() }.getOrElse { "" }
                error("Uventet svar fra oppgave, status: '${response.status}', body: '$body'")
            }
        }
    }

    /**
     * Nedlagte/sammenslåtte enheter som skal sendes til ny enhet. Kan skje av og til f.eks. pga. at avsender har brukt en gammel forside som de hadde liggende
     */
    private val videresendingEnheter = mapOf(
        "4708" to "4707",
        "4709" to "4710",
        "4717" to "4716",
        "4720" to "4719",
    )

    suspend fun opprettOppgaveBasertPåRutingOppgave(oppgave: RutingOppgave): String {
        log.info("Oppretter gosys-oppgave basert på ruting oppgave")

        val tildeltEnhet = when (oppgave.tildeltEnhetsnr) {
            in videresendingEnheter -> {
                val nyEnhet = videresendingEnheter[oppgave.tildeltEnhetsnr]
                log.warn {
                    "Mappet om nedlagt/sammenslått enhet: ${oppgave.tildeltEnhetsnr} til ny enhet: $nyEnhet for journalpostId: ${oppgave.journalpostId}"
                }
                nyEnhet
            }

            else -> {
                oppgave.tildeltEnhetsnr
            }
        }

        val requestBody = OppgaveRequestRutingOppgave(
            aktoerId = oppgave.aktoerId,
            orgnr = oppgave.orgnr,
            journalpostId = oppgave.journalpostId.toString(),
            beskrivelse = oppgave.beskrivelse,
            tema = oppgave.tema,
            oppgavetype = oppgave.oppgavetype,
            aktivDato = oppgave.aktivDato.toString(),
            fristFerdigstillelse = oppgave.fristFerdigstillelse.toString(),
            prioritet = oppgave.prioritet,
            opprettetAvEnhetsnr = oppgave.opprettetAvEnhetsnr,
            tildeltEnhetsnr = tildeltEnhet,
            behandlingstema = oppgave.behandlingstema,
            behandlingstype = oppgave.behandlingtype,
        )

        val tokenSet = azureAdClient.grant(scope)
        val response = client.post(baseUrl) {
            bearerAuth(tokenSet)
            setBody(requestBody)
        }

        return when (response.status) {
            HttpStatusCode.Created -> {
                response.body<OpprettOppgaveResponse>().id.toString()
            }

            else -> {
                val body = runCatching { response.bodyAsText() }.getOrElse { "" }
                error("Uventet svar fra oppgave, status: '${response.status}', body: '$body'")
            }
        }
    }

    suspend fun arkiverSøknad(aktørId: String, journalpostId: String): String {
        log.info { "Oppretter oppgave" }

        val requestBody = OppgaveRequest(
            aktoerId = aktørId,
            journalpostId = journalpostId,
            beskrivelse = BESKRIVELSE_OPPGAVE,
            temagruppe = TEMA_GRUPPE,
            tema = TEMA,
            oppgavetype = OPPGAVETYPE_JRF,
            behandlingstype = BEHANDLINGSTYPE,
            aktivDato = hentAktivDato(),
            fristFerdigstillelse = hentFristFerdigstillelse(),
            prioritet = OPPGAVE_PRIORITET_NORM,
        )
        val tokenSet = azureAdClient.grant(scope)
        val response = client.post(baseUrl) {
            bearerAuth(tokenSet)
            setBody(requestBody)
        }

        return when (response.status) {
            HttpStatusCode.Created -> {
                response.body<OpprettOppgaveResponse>().id.toString()
            }

            else -> {
                val body = runCatching { response.bodyAsText() }.getOrElse { "" }
                error("Uventet svar fra oppgave, status: '${response.status}', body: '$body'")
            }
        }
    }

    private fun hentFristFerdigstillelse() =
        LocalDate.now().toString()

    private fun hentAktivDato() =
        LocalDate.now().toString()

    private data class HentOppgaverResponse(
        val antallTreffTotalt: Long,
    )

    private data class OpprettOppgaveResponse(
        val id: Long,
    )
}

internal class OppgaveException(msg: String) : RuntimeException(msg)
