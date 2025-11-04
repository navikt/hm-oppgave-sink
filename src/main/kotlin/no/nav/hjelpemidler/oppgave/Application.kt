package no.nav.hjelpemidler.oppgave

import io.ktor.client.engine.cio.CIO
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.http.openid.TexasClient
import no.nav.hjelpemidler.oppgave.client.OppgaveClient
import no.nav.hjelpemidler.oppgave.mock.MockServer
import no.nav.hjelpemidler.oppgave.service.OpprettOppgaveForDigitalSøknad
import no.nav.hjelpemidler.oppgave.service.OpprettOppgaveForOverføring
import no.nav.hjelpemidler.oppgave.service.OpprettOppgaveForPapirsøknad
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import java.time.LocalDateTime

fun main() {
    if (Environment.current.isLocal) {
        MockServer().apply {
            setup()
            start()
        }
    }

    val engine = CIO.create()
    val texasClient = TexasClient(engine)
    val oppgaveClient = OppgaveClient(
        baseUrl = Configuration.OPPGAVE_BASE_URL,
        tokenSetProvider = texasClient.entraIdApplication(Configuration.OPPGAVE_SCOPE),
        engine = engine,
    )

    RapidApplication.create(no.nav.hjelpemidler.configuration.Configuration) { engine, _ ->
        engine.application.install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter(jsonMapper))
        }
        engine.application.routing {
            if (Environment.current.isDev) {
                post("/internal/rydd-opp-gosys-oppgaver") {
                    data class Request(
                        val aktoerId: String,
                        val before: LocalDateTime = LocalDateTime.now(),
                        val limit: Int = 100,
                    )

                    val req = call.receive<Request>()
                    call.respond(oppgaveClient.fjernGamleOppgaver(req.aktoerId, req.before, req.limit))
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
