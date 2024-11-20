package no.nav.hjelpemidler.oppgave.domain

import no.nav.hjelpemidler.oppgave.client.models.OpprettOppgaveRequest
import java.util.UUID

data class Søknad(
    val søknadId: UUID,
    val journalpostId: String,
    val sakstype: Sakstype,
    val fnrBruker: String,
    val prioritet: OpprettOppgaveRequest.Prioritet = OpprettOppgaveRequest.Prioritet.NORM,
)
