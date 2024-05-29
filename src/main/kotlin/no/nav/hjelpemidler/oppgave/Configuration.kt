package no.nav.hjelpemidler.oppgave

import no.nav.hjelpemidler.configuration.EnvironmentVariable

object Configuration {
    // Oppgave
    val OPPGAVE_BASE_URL by EnvironmentVariable
    val OPPGAVE_SCOPE by EnvironmentVariable

    // River
    val CONSUMED_EVENT_NAME by EnvironmentVariable
    val PRODUCED_EVENT_NAME by EnvironmentVariable

    // hm-soknadsbehandling-db
    val SOKNADSBEHANDLING_DB_URL by EnvironmentVariable
    val SOKNADSBEHANDLING_DB_SCOPE by EnvironmentVariable
}
