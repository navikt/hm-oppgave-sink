package no.nav.hjelpemidler.oppgave.test

import io.ktor.http.ParametersBuilder
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.TokenSet

class TestOpenIDClient(private val tokenSet: TokenSet) : OpenIDClient {
    override suspend fun grant(builder: ParametersBuilder.() -> Unit): TokenSet = tokenSet
}
