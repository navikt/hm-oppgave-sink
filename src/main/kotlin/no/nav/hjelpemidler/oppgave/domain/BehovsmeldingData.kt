package no.nav.hjelpemidler.oppgave.domain

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

data class BehovsmeldingData(
    val fnrBruker: String,
    val navnBruker: String,
    val fnrInnsender: String?,
    val soknadId: UUID,
    val soknad: JsonNode,
    val status: String,
    val kommunenavn: String?,
    val er_digital: Boolean,
    val soknadGjelder: String?,
) {
    fun erHast(): Boolean = !(soknad["soknad"]["hast"] == null || soknad["soknad"]["hast"].isNull)
}
