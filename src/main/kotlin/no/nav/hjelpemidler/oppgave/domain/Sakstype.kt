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

    fun toBehandlingstype(prioritet: OpprettOppgaveRequest.Prioritet): Behandlingstype {
        val høy = prioritet == OpprettOppgaveRequest.Prioritet.HOY
        return when (this) {
            SØKNAD -> if (høy) Behandlingstype.HASTESØKNAD else Behandlingstype.DIGITAL_SØKNAD
            BESTILLING -> if (høy) Behandlingstype.HASTEBESTILLING else Behandlingstype.BESTILLING
            BYTTE, BRUKERPASSBYTTE -> if (høy) Behandlingstype.HASTEBYTTE else Behandlingstype.DIGITALT_BYTTE
            BARNEBRILLER -> error("BARNEBRILLER støttes ikke")
        }
    }
}

enum class Behandlingstype(
    val beskrivelse: String,
    val eksternKode: String,
) {
    BESTILLING(beskrivelse = "Bestilling", eksternKode = "ae0281"),
    DIGITALT_BYTTE(beskrivelse = "Digitalt bytte", eksternKode = "ae0273"),
    DIGITAL_SØKNAD(beskrivelse = "Digital søknad", eksternKode = "ae0227"),
    HASTEBESTILLING(beskrivelse = "Hastebestilling", eksternKode = "ae0282"),
    HASTEBYTTE(beskrivelse = "Hastebytte", eksternKode = "ae0283"),
    HASTESØKNAD(beskrivelse = "Hastesøknad", eksternKode = "ae0286"),
    SØKNAD(beskrivelse = "Søknad", eksternKode = "ae0034"),
}
