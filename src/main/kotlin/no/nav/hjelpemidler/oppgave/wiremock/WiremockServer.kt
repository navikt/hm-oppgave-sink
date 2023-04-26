package no.nav.hjelpemidler.oppgave.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.hjelpemidler.http.openid.AzureADEnvironmentVariable
import no.nav.hjelpemidler.http.openid.TokenSet
import no.nav.hjelpemidler.oppgave.jsonMapper
import kotlin.time.Duration.Companion.hours

class WiremockServer {
    fun startServer() {
        val wiremockServer = WireMockServer(9098)
        wiremockServer
            .stubFor(
                WireMock.post(WireMock.urlPathMatching("/${AzureADEnvironmentVariable.AZURE_APP_TENANT_ID}/oauth2/v2.0/token"))
                    .willReturn(
                        WireMock.aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(jsonMapper.writeValueAsString(TokenSet.bearer(1.hours, ""))),
                    ),
            )
        wiremockServer
            .stubFor(
                WireMock.post(WireMock.urlPathMatching("/oppgave-aad"))
                    .willReturn(
                        WireMock.aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                // language=JSON
                                """
                                        {
                                          "id" : 54441,
                                          "tildeltEnhetsnr" : "4711",
                                          "journalpostId" : "453643544",
                                          "aktoerId" : "2816991252958",
                                          "beskrivelse" : "Digital søknad om hjelpemidler",
                                          "temagruppe" : "HJLPM",
                                          "tema" : "HJE",
                                          "oppgavetype" : "JFR",
                                          "behandlingstype" : "ae0227",
                                          "versjon" : 1,
                                          "opprettetAv" : "srv-digihotProxy",
                                          "prioritet" : "NORM",
                                          "status" : "OPPRETTET",
                                          "metadata" : { },
                                          "fristFerdigstillelse" : "2021-02-02",
                                          "aktivDato" : "2021-02-02",
                                          "opprettetTidspunkt" : "2021-02-02T14:33:35.177+01:00"
                                        }
                                        """,
                            ),
                    ),
            )
        wiremockServer
            .stubFor(
                WireMock.post(WireMock.urlPathMatching("/pdl"))
                    .willReturn(
                        WireMock.aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                // language=JSON
                                """{"data":{"hentIdenter":{"identer": [{"ident": "aktorid","historisk": false,"type": "AKTORID"}]}}}""",
                            ),
                    ),
            )
        wiremockServer.start()
    }
}
