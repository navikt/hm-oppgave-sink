package no.nav.hjelpemidler.oppgave.domain

import no.nav.hjelpemidler.oppgave.client.models.OpprettOppgaveRequest

enum class Sakstype {
    SØKNAD,
    BESTILLING,
    BYTTE,
    BRUKERPASSBYTTE,
    BARNEBRILLER,
    ;

    fun toBeskrivelse() = when (this) {
        BYTTE, BRUKERPASSBYTTE -> "Digitalt bytte av hjelpemidler"
        else -> "Digital søknad om hjelpemidler"
    }

    fun toBehandlingstype(prioritet: OpprettOppgaveRequest.Prioritet): String? {
        if (prioritet == OpprettOppgaveRequest.Prioritet.HOY) return null
        return when (this) {
            BYTTE, BRUKERPASSBYTTE -> "ae0273" // Digitalt bytte
            else -> "ae0227" // Digital søknad
        }
    }

    fun toBehandlingstema(prioritet: OpprettOppgaveRequest.Prioritet): String? {
        if (prioritet != OpprettOppgaveRequest.Prioritet.HOY) return null
        return when (this) {
            BYTTE, BRUKERPASSBYTTE -> "ab0521" // Hastebytte
            else -> "ab0520" // Hastesøknad
        }
    }
}
