package no.nav.hjelpemidler.oppgave

import no.nav.hjelpemidler.configuration.EnvironmentVariable

object Configuration {
    // Oppgave
    val OPPGAVE_BASE_URL by EnvironmentVariable

    // Proxy
    val PROXY_SCOPE by EnvironmentVariable

    // River
    val CONSUMED_EVENT_NAME by EnvironmentVariable
    val PRODUCED_EVENT_NAME by EnvironmentVariable
}
