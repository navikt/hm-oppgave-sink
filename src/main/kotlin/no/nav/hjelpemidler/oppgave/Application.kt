package no.nav.hjelpemidler.oppgave

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.http.openid.azureADClient
import no.nav.hjelpemidler.oppgave.client.OppgaveClient
import no.nav.hjelpemidler.oppgave.mock.MockServer
import no.nav.hjelpemidler.oppgave.service.OpprettOppgaveForDigitalSøknad
import no.nav.hjelpemidler.oppgave.service.OpprettOppgaveForOverføring
import no.nav.hjelpemidler.oppgave.service.OpprettOppgaveForPapirsøknad
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.seconds

fun main() {
    if (Environment.current.tier.isLocal) {
        MockServer().apply {
            setup()
            start()
        }
    }

    val azureAdClient = azureADClient {
        cache(leeway = 10.seconds) {
            maximumSize = 100
        }
    }

    val oppgaveClient = OppgaveClient(
        baseUrl = Configuration.OPPGAVE_BASE_URL,
        scope = Configuration.OPPGAVE_SCOPE,
        azureAdClient = azureAdClient,
    )

    RapidApplication.create(no.nav.hjelpemidler.configuration.Configuration.current) { engine, _ ->
        engine.application.routing {
            if (Environment.current.tier.isDev) {
                post("/internal/rydd-opp-gosys-oppgaver") {
                    data class Request(
                        val aktoerId: String,
                        val before: LocalDateTime = LocalDateTime.now(),
                        val limit: Int = 100,
                    )
                    val req = call.receive<Request>()
                    oppgaveClient.fjernGamleOppgaver(req.aktoerId, req.before, req.limit)
                    call.respond("OK")
                }
            }
        }
    }
        .apply {
            OpprettOppgaveForDigitalSøknad(this, oppgaveClient)
            OpprettOppgaveForPapirsøknad(this, oppgaveClient)
            OpprettOppgaveForOverføring(this, oppgaveClient)
        }
        .start()
}
