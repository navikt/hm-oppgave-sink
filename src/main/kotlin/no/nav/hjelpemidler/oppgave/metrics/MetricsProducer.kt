package no.nav.hjelpemidler.oppgave.metrics

import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.oppgave.publish
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

class MetricsProducer(private val context: MessageContext) {
    fun rutingOppgaveForsøktHåndtert(type: String) = hendelseOpprettet(RUTING_OPPGAVE_FORSØKT_HÅNDTERT, mapOf("type" to type))

    fun rutingOppgaveOpprettet(type: String) = hendelseOpprettet(RUTING_OPPGAVE_OPPRETTET, mapOf("type" to type))

    fun rutingOppgaveEksisterteAllerede(type: String) = hendelseOpprettet(RUTING_OPPGAVE_EKSISTERTE_ALLEREDE, mapOf("type" to type))

    fun rutingOppgaveException(type: String) = hendelseOpprettet(RUTING_OPPGAVE_EXCEPTION, mapOf("type" to type))

    fun hendelseOpprettet(
        measurement: String,
        fields: Map<String, Any>,
        tags: Map<String, String> = emptyMap(),
    ) {
        context.publish(
            measurement,
            mapOf(
                "eventId" to UUID.randomUUID(),
                "eventName" to "hm-bigquery-sink-hendelse",
                "schemaId" to "hendelse_v2",
                "payload" to mapOf(
                    "opprettet" to LocalDateTime.now(),
                    "navn" to measurement,
                    "kilde" to "hm-oppgave-sink",
                    "data" to fields.mapValues { it.value.toString() }
                        .plus(tags)
                        .filterKeys { it != "counter" },
                ),
            ),
        )
    }

    companion object {
        private const val PREFIX = "hm-oppgave-sink"
        const val RUTING_OPPGAVE_FORSØKT_HÅNDTERT = "$PREFIX.rutingoppgave.forsokt.haandtert"
        const val RUTING_OPPGAVE_OPPRETTET = "$PREFIX.rutingoppgave.opprettet"
        const val RUTING_OPPGAVE_EKSISTERTE_ALLEREDE = "$PREFIX.rutingoppgave.eksisterte.allerede"
        const val RUTING_OPPGAVE_EXCEPTION = "$PREFIX.rutingoppgave.exception"
    }
}
