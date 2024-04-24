package no.nav.hjelpemidler.oppgave.client

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import mu.KotlinLogging
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.bearerAuth
import no.nav.hjelpemidler.oppgave.domain.BehovsmeldingData
import java.util.UUID

private val log = KotlinLogging.logger {}

class SoknadsbehandlingDbClient(
    private val baseUrl: String,
    private val scope: String,
    private val azureAdClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val client =
        createHttpClient(engine) {
            expectSuccess = true
            defaultRequest {
                header("X-Correlation-ID", UUID.randomUUID().toString())
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
            }
        }

    suspend fun hentBehovsmelding(behovsmeldingId: UUID): BehovsmeldingData {
        log.info { "Henter metadata for $behovsmeldingId" }
        val tokenSet = azureAdClient.grant(scope)
        val response =
            client.get("$baseUrl/api/soknadsdata/bruker/$behovsmeldingId") {
                bearerAuth(tokenSet)
            }
        return response.body()
    }
}
