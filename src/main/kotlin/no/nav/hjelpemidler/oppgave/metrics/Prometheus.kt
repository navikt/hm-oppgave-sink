package no.nav.hjelpemidler.oppgave.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

object Prometheus {
    private val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    val oppgaveOpprettetCounter = Counter
        .build()
        .name("hm_soknad_opprettet_oppgave")
        .help("Antall oppgaver opprettet i oppgave")
        .register(collectorRegistry)
}
