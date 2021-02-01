package no.nav.hjelpemidler.oppgave.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.oppgave.metrics.Prometheus
import no.nav.hjelpemidler.oppgave.oppgave.OppgaveClient
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class OppgaveDataSink(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveClient
) :
    River.PacketListener {

    companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private fun soknadToJson(soknad: JsonNode): String = objectMapper.writeValueAsString(soknad)

    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("fodselNrBruker", "navnBruker", "soknad", "soknadId") }
        }.register(this)
    }

    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.navnBruker get() = this["navnBruker"].textValue()
    private val JsonMessage.soknadId get() = this["soknadId"].textValue()
    private val JsonMessage.soknad get() = this["soknad"]

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val soknadData = SoknadData(
                        fnrBruker = packet.fnrBruker,
                        navnBruker = packet.navnBruker,
                        soknadJson = soknadToJson(packet.soknad),
                        soknadId = UUID.fromString(packet.soknadId)
                    )
                    logger.info { "Søknad til arkivering mottatt: ${soknadData.soknadId}" }
                    val opggaveId = opprettOppgave("", "", soknadData.soknadId)
                    forward(soknadData, opggaveId, context)
                }
            }
        }
    }

    private suspend fun opprettOppgave(aktorId: String, journalpostId: String, soknadId: UUID) =
        kotlin.runCatching {
            oppgaveClient.arkiverSoknad(aktorId, journalpostId)
        }.onSuccess {
            logger.info("Oppgave opprettet: $soknadId")
            Prometheus.hentetAktorIdCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under opprettelse av oppgave: $soknadId" }
        }.getOrThrow()

    private fun CoroutineScope.forward(søknadData: SoknadData, joarkRef: String, context: RapidsConnection.MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.send(søknadData.fnrBruker, søknadData.toJson(joarkRef))
            Prometheus.oppgaveOpprettetCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Oppgave opprettet for søknad: ${søknadData.soknadId}")
                    sikkerlogg.info("Oppgave opprettet for søknad: ${søknadData.soknadId}, fnr: ${søknadData.fnrBruker})")
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    logger.error("Failed: ${it.message}. Soknad: ${søknadData.soknadId}")
                }
            }
        }
    }
}

internal data class SoknadData(
    val fnrBruker: String,
    val navnBruker: String,
    val soknadId: UUID,
    val soknadJson: String,
) {
    internal fun toJson(joarkRef: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["soknadId"] = this.soknadId
            it["@event_name"] = "OppgaveOpprettet"
            it["@opprettet"] = LocalDateTime.now()
            it["fnrBruker"] = this.fnrBruker
            it["joarkRef"] = joarkRef
        }.toJson()
    }
}
