package no.nav.hjelpemidler.oppgave.oppgave

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.oppgave.AzureClient
import no.nav.hjelpemidler.oppgave.oppgave.model.OppgaveRequest
import no.nav.hjelpemidler.oppgave.oppgave.model.OppgaveRequestRutingOppgave
import no.nav.hjelpemidler.oppgave.service.RutingOppgave
import java.time.LocalDate
import java.util.UUID

private val logger = KotlinLogging.logger {}

class OppgaveClient(
    private val baseUrl: String,
    private val accesstokenScope: String,
    private val azureClient: AzureClient,
) {

    companion object {
        private val objectMapper = ObjectMapper()
        const val BEHANDLINGSTYPE = "ae0227" // Digital søknad
        const val OPPGAVETYPE_JRF = "JFR"
        const val OPPGAVE_PRIORITET_NORM = "NORM"
        const val TEMA = "HJE"
        const val TEMA_GRUPPE = "HJLPM"
        const val BESKRIVELSE_OPPGAVE = "Digital søknad om hjelpemidler"
    }

    suspend fun harAlleredeOppgaveForJournalpost(journalpostId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val correlationID = UUID.randomUUID().toString()

                baseUrl.httpGet(
                    listOf(
                        Pair("journalpostId", journalpostId),
                        Pair("statuskategori", "AAPEN"),
                        Pair("oppgavetype", "JFR"),
                        Pair("oppgavetype", "FDR"),
                    ),
                )
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .header("X-Correlation-ID", correlationID)
                    .awaitObject(
                        object : ResponseDeserializable<JsonNode> {
                            override fun deserialize(content: String): JsonNode {
                                return ObjectMapper().readTree(content)
                            }
                        },
                    )
                    .let {
                        when (it.has("antallTreffTotalt")) {
                            true -> it.at("/antallTreffTotalt").let { if (it.isNull()) 0 else it.asInt() } > 0
                            false -> throw OppgaveException("Klarte ikke å sjekke om oppgave finnes alt")
                        }
                    }
            }
                .onFailure {
                    logger.error(it) { "Api kallet feilet for harAlleredeOppgaveForJournalpost" }
                }
        }
            .getOrThrow()
    }

    // Nedlagte/sammenslåtte enheter som skal sendes til ny enhet. Kan skje av og til feks. pga. at avsender har brukt en gammel forside som de hadde liggende
    private val enhetVideresendingMap = mapOf("4708" to "4707", "4709" to "4710", "4717" to "4716", "4720" to "4719")

    suspend fun opprettOppgaveBasertPåRutingOppgave(oppgave: RutingOppgave): String {
        logger.info("Oppretter gosys-oppgave basert på ruting oppgave")

        val tildeltEnhetsnr = if (oppgave.tildeltEnhetsnr in enhetVideresendingMap) {
            val nyEnhet = enhetVideresendingMap[oppgave.tildeltEnhetsnr]
            logger.warn { "Mappa om nedlagt/sammenslått enhetsnr ${oppgave.tildeltEnhetsnr} til $nyEnhet for journalpostId ${oppgave.journalpostId}" }
            nyEnhet
        } else {
            oppgave.tildeltEnhetsnr
        }

        val requestBody = OppgaveRequestRutingOppgave(
            oppgave.aktoerId,
            oppgave.orgnr,
            oppgave.journalpostId.toString(),
            oppgave.beskrivelse,
            oppgave.tema,
            oppgave.oppgavetype,
            oppgave.aktivDato.toString(),
            oppgave.fristFerdigstillelse.toString(),
            oppgave.prioritet,
            oppgave.opprettetAvEnhetsnr,
            tildeltEnhetsnr,
            oppgave.behandlingstema,
            oppgave.behandlingtype,
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
                        },
                    )
                    .let {
                        when (it.has("id")) {
                            true -> it["id"].toString()
                            false -> throw OppgaveException("Klarte ikke å opprette oppgave")
                        }
                    }
            }
                .onFailure {
                    if (it is FuelError) {
                        val exp = it
                        logger.error(it) {
                            "Klarte ikke opprette oppgave basert på ruting-oppgave: ${
                                exp.errorData.toString(
                                    Charsets.UTF_8,
                                )
                            }"
                        }
                    } else {
                        logger.error(it) { "Klarte ikke opprette oppgave basert på ruting-oppgave" }
                    }
                }
        }
            .getOrThrow()
    }

    suspend fun arkiverSoknad(aktorId: String, journalpostId: String): String {
        logger.info { "Oppretter oppgave" }

        val requestBody = OppgaveRequest(
            aktorId, journalpostId, BESKRIVELSE_OPPGAVE,
            TEMA_GRUPPE, TEMA, OPPGAVETYPE_JRF, BEHANDLINGSTYPE,
            hentAktivDato(), hentFristFerdigstillelse(), OPPGAVE_PRIORITET_NORM,
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
                        },
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

internal class OppgaveException(msg: String) : RuntimeException(msg)
