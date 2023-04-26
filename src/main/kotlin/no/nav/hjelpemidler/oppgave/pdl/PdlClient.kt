package no.nav.hjelpemidler.oppgave.pdl

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import mu.KotlinLogging
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.bearerAuth
import no.nav.hjelpemidler.pdl.HentIdenter
import no.nav.hjelpemidler.pdl.enums.IdentGruppe
import java.net.URL
import java.util.UUID

private val log = KotlinLogging.logger {}

class PdlClient(
    baseUrl: String,
    private val scope: String,
    private val azureAdClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val client = GraphQLKtorClient(
        url = URL(baseUrl),
        httpClient = HttpClient(engine = engine) {
            expectSuccess = true
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(maxRetries = 5)
                exponentialDelay()
            }
            defaultRequest {
                header("X-Correlation-ID", UUID.randomUUID().toString())
            }
        },
    )

    suspend fun hentAktørId(fnr: String): String {
        log.info { "Henter aktørId i PDL" }
        val tokenSet = azureAdClient.grant(scope)
        val request = HentIdenter(HentIdenter.Variables(fnr, listOf(IdentGruppe.AKTORID)))
        val response = client.execute(request) {
            bearerAuth(tokenSet)
        }
        val result = response.resultOrThrow()
        return checkNotNull(result.hentIdenter?.identer?.first()?.ident) {
            "Fant ikke aktørId i PDL"
        }
    }
}

fun <T> GraphQLClientResponse<T>.resultOrThrow(): T {
    val errors = this.errors
    val data = this.data
    return when {
        errors != null -> error("Feil fra GraphQL-tjeneste: '${errors.joinToString { it.message }}'")
        data != null -> data
        else -> error("Både data og errors var null!")
    }
}
