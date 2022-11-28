package no.nav.hjelpemidler.oppgave.pdl

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

private val logger = KotlinLogging.logger {}

class PdlClient(
    private val baseUrl: String,
    private val accesstokenScope: String,
    private val azureClient: AzureClient
) {

    companion object {
        fun aktorQuery(fnr: String) =
            """
        {
            "query": "query(${'$'}ident: ID!) { hentIdenter(ident:${'$'}ident, grupper: [AKTORID]) { identer { ident,gruppe, historisk } } }",
            "variables": {
                "ident": "$fnr"
            }
        }            
        """
    }

    suspend fun hentAktorId(fnr: String): String {
        logger.info { "Henter aktørid" }

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val token = azureClient.getToken(accesstokenScope).accessToken

                "$baseUrl".httpPost()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $token")
                    .header("Tema", "HJE")
                    .jsonBody(aktorQuery(fnr))
                    .awaitObject(
                        object : ResponseDeserializable<JsonNode> {
                            override fun deserialize(content: String): JsonNode {
                                return ObjectMapper().readTree(content)
                            }
                        }
                    )
                    .let {
                        when (it.hasNonNull("errors")) {
                            true -> throw PdlException(it["errors"].toString())
                            false -> it["data"]["hentIdenter"]["identer"][0]["ident"].asText()
                        }
                    }
            }
                .onFailure {
                    logger.error { it.message }
                }
        }
            .getOrThrow()
    }
}

internal class PdlException(msg: String) : RuntimeException(msg)
