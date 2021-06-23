package no.nav.hjelpemidler.oppgave.service

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import java.time.LocalDateTime
import java.util.UUID

data class SoknadData(
    val fnrBruker: String,
    val soknadId: UUID,
    val joarkRef: String,
) {
    internal fun toJson(oppgaveId: String, eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["soknadId"] = this.soknadId
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fnrBruker"] = this.fnrBruker
            it["oppgaveId"] = oppgaveId
        }.toJson()
    }
}
