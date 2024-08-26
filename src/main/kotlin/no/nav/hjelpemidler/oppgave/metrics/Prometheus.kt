package no.nav.hjelpemidler.oppgave.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

object Prometheus {
    private val registry: MeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val oppgaveOpprettetCounter: Counter = registry.counter("hm_soknad_opprettet_oppgave")
}
