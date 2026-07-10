package no.nav.hjelpemidler.oppgave.domain

import no.nav.hjelpemidler.oppgave.client.models.OpprettOppgaveRequest

data class Delbestilling(
    val saksnummer: Long,
    val fnrBruker: String,
    val sakstype: Sakstype,
    val journalpostId: String? = null,
    val prioritet: OpprettOppgaveRequest.Prioritet,
)
