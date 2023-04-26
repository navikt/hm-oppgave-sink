package no.nav.hjelpemidler.oppgave.oppgave

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.header
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
import no.nav.hjelpemidler.oppgave.oppgave.model.OpprettBehandleSakOppgaveRequest
import java.time.LocalDate
import java.util.UUID

private val log = KotlinLogging.logger {}

class OppgaveClientV2(
    private val baseUrl: String,
    private val scope: String,
    private val azureAdClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    companion object {
        const val BEHANDLINGSTYPE = "ae0227" // Digital søknad
        const val OPPGAVETYPE_BEH_SAK = "BEH_SAK"
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

    suspend fun opprettBehandleSakOppgave(
        aktørId: String,
        journalpostId: String,
        beskrivelse: String,
        enhet: String,
    ): String {
        log.info { "Oppretter oppgave for ferdigstilt journalpost" }

        val requestBody = OpprettBehandleSakOppgaveRequest(
            aktoerId = aktørId,
            journalpostId = journalpostId,
            beskrivelse = beskrivelse,
            temagruppe = TEMA_GRUPPE,
            tema = TEMA,
            oppgavetype = OPPGAVETYPE_BEH_SAK,
            behandlingstype = BEHANDLINGSTYPE,
            aktivDato = hentAktivDato(),
            fristFerdigstillelse = hentFristFerdigstillelse(),
            prioritet = OPPGAVE_PRIORITET_NORM,
            tildeltEnhetsnr = enhet,
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

    private data class OpprettOppgaveResponse(
        val id: Long,
    )
}
