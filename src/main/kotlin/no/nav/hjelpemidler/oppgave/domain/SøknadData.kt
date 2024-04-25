package no.nav.hjelpemidler.oppgave.domain

import java.util.UUID

data class SøknadData(
    val søknadId: UUID,
    val journalpostId: String,
    val sakstype: Sakstype,
    val fnrBruker: String,
)
