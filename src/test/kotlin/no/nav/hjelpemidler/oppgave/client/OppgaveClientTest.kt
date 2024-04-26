package no.nav.hjelpemidler.oppgave.client

import com.github.tomakehurst.wiremock.client.WireMock.created
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.unauthorized
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.http.openid.TokenSet
import no.nav.hjelpemidler.oppgave.client.models.OpprettOppgaveRequest
import no.nav.hjelpemidler.oppgave.client.models.SokOppgaverResponse
import no.nav.hjelpemidler.oppgave.mock.lagOppgave
import no.nav.hjelpemidler.oppgave.mock.withBody
import no.nav.hjelpemidler.oppgave.test.TestOpenIDClient
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

@WireMockTest
class OppgaveClientTest {
    private val engine = CIO.create()
    private val oppgave = lagOppgave()

    private lateinit var client: OppgaveClient

    @BeforeTest
    fun setUp(wireMockRuntimeInfo: WireMockRuntimeInfo) {
        client = OppgaveClient(
            baseUrl = "${wireMockRuntimeInfo.httpBaseUrl}/api/v1/oppgaver",
            scope = "test",
            azureAdClient = TestOpenIDClient(TokenSet.bearer(1.hours, "")),
            engine = engine,
        )
    }

    @Test
    fun `Har oppgave for journalpost`() = runTest {
        stubFor(
            get(urlPathEqualTo("/api/v1/oppgaver"))
                .willReturn(ok().withBody(SokOppgaverResponse(1, listOf(oppgave)))),
        )

        val result = client.harOppgaveForJournalpost("1")

        result shouldBe true
    }

    @Test
    fun `Har ikke oppgave for journalpost`() = runTest {
        stubFor(
            get(urlPathEqualTo("/api/v1/oppgaver"))
                .willReturn(ok().withBody(SokOppgaverResponse(0, listOf()))),
        )

        val result = client.harOppgaveForJournalpost("1")

        result shouldBe false
    }

    @Test
    fun `Oppretter oppgave`() = runTest {
        stubFor(
            post(urlPathEqualTo("/api/v1/oppgaver"))
                .willReturn(created().withBody(oppgave)),
        )

        val result = client.opprettOppgave(
            OpprettOppgaveRequest(
                tema = "HJE",
                oppgavetype = "JFR",
                aktivDato = LocalDate.now(),
                prioritet = OpprettOppgaveRequest.Prioritet.NORM,
            ),
        )

        result shouldBe oppgave
    }

    @Test
    fun `Mangler tilgang`() = runTest {
        stubFor(
            get(urlPathEqualTo("/api/v1/oppgaver"))
                .willReturn(unauthorized().withBody("401")),
        )

        val thrown = shouldThrow<ClientRequestException> {
            client.harOppgaveForJournalpost("1")
        }

        thrown.response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `Ukjent feil`() = runTest {
        stubFor(
            get(urlPathEqualTo("/api/v1/oppgaver"))
                .willReturn(serverError().withBody("500")),
        )

        val thrown = shouldThrow<ServerResponseException> {
            client.harOppgaveForJournalpost("1")
        }

        thrown.response.status shouldBe HttpStatusCode.InternalServerError
    }
}
