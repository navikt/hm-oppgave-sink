package no.nav.hjelpemidler.oppgave

import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import com.github.navikt.tbd_libs.rapids_and_rivers.createDefaultKafkaRapidFromEnv
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.helse.rapids_rivers.RapidApplication.Builder
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.http.openid.azureADClient
import no.nav.hjelpemidler.oppgave.client.OppgaveClient
import no.nav.hjelpemidler.oppgave.mock.MockServer
import no.nav.hjelpemidler.oppgave.service.OpprettOppgaveForDigitalSøknad
import no.nav.hjelpemidler.oppgave.service.OpprettOppgaveForOverføring
import no.nav.hjelpemidler.oppgave.service.OpprettOppgaveForPapirsøknad
import java.net.InetAddress
import java.time.LocalDateTime
import java.util.UUID
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

    val env = no.nav.hjelpemidler.configuration.Configuration.current
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM)
    val kafkaRapid = createDefaultKafkaRapidFromEnv(
        factory = ConsumerProducerFactory(AivenConfig.default),
        meterRegistry = meterRegistry,
        env = env,
    )

    Builder(
        appName = env["RAPID_APP_NAME"]!!,
        instanceId = when (env.containsKey("NAIS_APP_NAME")) {
            true -> InetAddress.getLocalHost().hostName
            false -> UUID.randomUUID().toString()
        },
        rapid = kafkaRapid,
        meterRegistry = meterRegistry,
    )
        .withKtorModule {
            routing {
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
        .build()
        .apply {
            OpprettOppgaveForDigitalSøknad(this, oppgaveClient)
            OpprettOppgaveForPapirsøknad(this, oppgaveClient)
            OpprettOppgaveForOverføring(this, oppgaveClient)
        }
        .start()
}
