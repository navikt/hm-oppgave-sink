package no.nav.hjelpemidler.oppgave.domain

import java.util.UUID

data class SøknadData(
    val fnrBruker: String,
    val soknadId: UUID,
    val joarkRef: String,
    val sakstype: Sakstype,
)
