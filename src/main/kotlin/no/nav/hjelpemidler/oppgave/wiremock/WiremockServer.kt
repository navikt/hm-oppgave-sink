package no.nav.hjelpemidler.oppgave.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.hjelpemidler.oppgave.Configuration

internal class WiremockServer(private val configuration: Configuration) {

    fun startServer() {
        val wiremockServer = WireMockServer(9098)
        wiremockServer
            .stubFor(
                WireMock.post(WireMock.urlPathMatching("/${configuration.azure.tenantId}/oauth2/v2.0/token"))
                    .willReturn(
                        WireMock.aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """{
                        "token_type": "Bearer",
                        "expires_in": 3599,
                        "access_token": "1234abc"
                    }"""
                            )
                    )
            )
        wiremockServer
            .stubFor(
                WireMock.post(WireMock.urlPathMatching("/oppgave"))
                    .willReturn(
                        WireMock.aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                    "id": "12345"
                                }
                                      """
                            )
                    )
            )
        wiremockServer
            .stubFor(
                WireMock.post(WireMock.urlPathMatching("/pdl"))
                    .willReturn(
                        WireMock.aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """{"data":{"hentIdenter":{"identer": [{"ident": "aktorid","historisk": false,"type": "AKTORID"}]}}}"""
                            )
                    )
            )
        wiremockServer.start()
    }
}
