package no.nav.hjelpemidler.oppgave.service

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.oppgave.metrics.Prometheus

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class PapirsoeknadSink(
    rapidsConnection: RapidsConnection,
) :
    River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "PapirSoeknadMidlertidigJournalfoert") }
            validate { it.requireKey("fodselNrBruker") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        logger.info(packet.toString())
        Prometheus.papirsoeknadMottattCounter.inc()
    }
}
