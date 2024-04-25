package no.nav.hjelpemidler.oppgave.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.oppgave.Configuration
import no.nav.hjelpemidler.oppgave.client.OppgaveClient
import no.nav.hjelpemidler.oppgave.domain.Sakstype
import no.nav.hjelpemidler.oppgave.domain.SøknadData
import no.nav.hjelpemidler.oppgave.metrics.Prometheus
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}
private val secureLog = KotlinLogging.logger("tjenestekall")

class OppgaveDataSink(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveClient,
    private val consumedEventName: String = Configuration.CONSUMED_EVENT_NAME,
    private val producedEventName: String = Configuration.PRODUCED_EVENT_NAME,
) : PacketListenerWithOnError {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", consumedEventName) }
            validate { it.requireKey("fnrBruker", "joarkRef", "soknadId", "eventId", "sakstype") }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.joarkRef get() = this["joarkRef"].textValue()
    private val JsonMessage.soknadId get() = this["soknadId"].textValue()

    private val JsonMessage.sakstype get() = Sakstype.valueOf(this["sakstype"].textValue())

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        if (skipEvent(UUID.fromString(packet.eventId))) {
            log.info { "Hopper over event i skipList, eventId: ${packet.eventId}" }
            return
        }

        try {
            val data = SøknadData(
                fnrBruker = packet.fnrBruker,
                joarkRef = packet.joarkRef,
                soknadId = UUID.fromString(packet.soknadId),
                sakstype = packet.sakstype,
            )
            log.info { "Arkivert søknad mottatt, søknadId: ${data.soknadId}" }
            val oppgaveId = runBlocking(Dispatchers.IO) { opprettOppgave(data) }
            context.publish(data.fnrBruker, toJson(data, oppgaveId, producedEventName))
            Prometheus.oppgaveOpprettetCounter.inc()
            log.info("Oppgave opprettet for søknadId: ${data.soknadId}")
            secureLog.info("Oppgave opprettet for søknadId: ${data.soknadId}, fnrBruker: ${data.fnrBruker})")
        } catch (e: Exception) {
            throw RuntimeException("Håndtering av eventId: ${packet.eventId} feilet", e)
        }
    }

    private fun skipEvent(eventId: UUID): Boolean {
        val skipList = setOf<UUID>()
        return eventId in skipList
    }

    private suspend fun opprettOppgave(søknadData: SøknadData): String =
        runCatching { oppgaveClient.opprettOppgave(søknadData) }
            .onSuccess { log.info("Oppgave opprettet: ${søknadData.soknadId}") }
            .onFailure { log.error(it) { "Feilet under opprettelse av oppgave for søknadId: ${søknadData.soknadId}" } }
            .getOrThrow()
}

fun toJson(
    søknadData: SøknadData,
    oppgaveId: String,
    producedEventName: String,
): String {
    return JsonMessage("{}", MessageProblems("")).also {
        it["soknadId"] = søknadData.soknadId
        it["eventName"] = producedEventName
        it["opprettet"] = LocalDateTime.now()
        it["fnrBruker"] = søknadData.fnrBruker
        it["oppgaveId"] = oppgaveId
        it["sakstype"] = søknadData.sakstype
    }.toJson()
}
