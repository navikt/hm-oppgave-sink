package no.nav.hjelpemidler.oppgave.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.created
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import no.nav.hjelpemidler.http.openid.TokenSet
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import java.io.Closeable
import kotlin.time.Duration.Companion.hours

class MockServer : Closeable {
    private val server = WireMockServer(9900)

    fun setup() {
        server.stubFor(
            post(urlPathEqualTo("/token"))
                .willReturn(ok().withBody(TokenSet.bearer(1.hours, ""))),
        )

        server.stubFor(
            get(urlPathEqualTo("/api/v1/oppgaver"))
                .willReturn(ok().withBody(lagOppgave())),
        )

        server.stubFor(
            post(urlPathEqualTo("/api/v1/oppgaver"))
                .willReturn(created().withBody(lagOppgave())),
        )
    }

    fun start() {
        server.start()
    }

    override fun close() {
        server.shutdown()
    }
}

fun <T : Any> ResponseDefinitionBuilder.withBody(body: T): ResponseDefinitionBuilder = this
    .withHeader("Content-Type", "application/json")
    .withJsonBody(jsonMapper.valueToTree(body))
