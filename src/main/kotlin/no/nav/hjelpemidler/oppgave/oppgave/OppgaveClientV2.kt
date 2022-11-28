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
import no.nav.hjelpemidler.oppgave.AzureClient
import no.nav.hjelpemidler.oppgave.oppgave.model.OpprettBehandleSakOppgaveRequest
import java.time.LocalDate
import java.util.UUID

private val logger = KotlinLogging.logger {}

class OppgaveClientV2(
    private val baseUrl: String,
    private val accesstokenScope: String,
    private val azureClient: AzureClient
) {

    companion object {
        private val objectMapper = ObjectMapper()
        const val BEHANDLINGSTYPE = "ae0227" // Digital søknad
        const val OPPGAVETYPE_BEH_SAK = "BEH_SAK"
        const val OPPGAVE_PRIORITET_NORM = "NORM"
        const val TEMA = "HJE"
        const val TEMA_GRUPPE = "HJLPM"
        const val BESKRIVELSE_OPPGAVE = "Digital søknad om hjelpemidler"
    }

    suspend fun opprettBehandleSakOppgave(aktorId: String, journalpostId: String, enhet: String, dokumentBeskrivelse: String): String {
        logger.info { "Oppretter oppgave for ferdigstilt journalpost" }

        val requestBody = OpprettBehandleSakOppgaveRequest(
            aktorId, journalpostId, dokumentBeskrivelse,
            TEMA_GRUPPE, TEMA, OPPGAVETYPE_BEH_SAK, BEHANDLINGSTYPE,
            hentAktivDato(), hentFristFerdigstillelse(), OPPGAVE_PRIORITET_NORM, enhet
        )

        val jsonBody = objectMapper.writeValueAsString(requestBody)

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val correlationID = UUID.randomUUID().toString()
                logger.info("DEBUG: akriverSøknad correlationID=$correlationID")

                baseUrl.httpPost()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", correlationID)
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
                            true -> it["id"].toString()
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

internal class OppgaveExceptionV2(msg: String) : RuntimeException(msg)
