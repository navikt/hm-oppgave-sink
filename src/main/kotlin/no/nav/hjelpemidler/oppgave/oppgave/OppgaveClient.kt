package no.nav.hjelpemidler.oppgave.oppgave

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.httpPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.oppgave.oppgave.model.OppgaveRequest
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

class OppgaveClient(
    private val baseUrl: String,
    private val accesstokenScope: String,
    private val azureClient: AzureClient
) {

    companion object {
        private val objectMapper = ObjectMapper()
        const val BEHANDLINGSTYPE = "ae0227"
        const val OPPGAVETYPE_JRF = "JFR"
        const val OPPGAVE_PRIORITET_NORM = "NORM"
        const val TEMA = "HJE"
        const val TEMA_GRUPPE = "HJLPM"
        const val BESKRIVELSE_OPPGAVE = "Digital søknad om hjelpemidler"
    }

    suspend fun arkiverSoknad(aktorId: String, journalpostId: String): String {
        logger.info { "Oppretter oppgave" }

        val requestBody = OppgaveRequest(
            aktorId, journalpostId, BESKRIVELSE_OPPGAVE,
            TEMA_GRUPPE, TEMA, OPPGAVETYPE_JRF, BEHANDLINGSTYPE,
            hentAktivDato(), hentFristFerdigstillelse(), OPPGAVE_PRIORITET_NORM
        )

        val jsonBody = objectMapper.writeValueAsString(requestBody)

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl".httpPost()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .jsonBody(jsonBody)
                    .awaitObject(
                        object : ResponseDeserializable<JsonNode> {
                            override fun deserialize(content: String): JsonNode {
                                return ObjectMapper().readTree(content)
                            }
                        }
                    )
                    .let {
                        when (it.has("id")) {
                            true -> it["id"].textValue()
                            false -> throw OppgaveException("Klarte ikke å opprette oppgave")
                        }
                    }
            }
                .onFailure {
                    logger.error { it.message }
                }
        }
            .getOrThrow()
    }

    private fun hentFristFerdigstillelse() =
        LocalDate.now().toString()

    private fun hentAktivDato() =
        LocalDate.now().toString()
}

internal class OppgaveException(msg: String) : RuntimeException(msg)
