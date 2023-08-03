package no.nav.hjelpemidler.oppgave.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

object Prometheus {
    private val collectorRegistry = CollectorRegistry.defaultRegistry

    val oppgaveOpprettetCounter = Counter
        .build()
        .name("hm_soknad_opprettet_oppgave")
        .help("Antall oppgaver opprettet i oppgave")
        .register(collectorRegistry)

    val hentetAktørIdCounter = Counter
        .build()
        .name("hm_soknad_hentet_aktorId")
        .help("Antall aktørId'er hentet fra PDL")
        .register(collectorRegistry)
}
