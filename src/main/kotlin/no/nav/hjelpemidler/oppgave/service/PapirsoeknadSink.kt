package no.nav.hjelpemidler.oppgave.service

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.oppgave.metrics.Prometheus

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class PapirsoeknadSink(
    rapidsConnection: RapidsConnection,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "PapirSoeknadMidlertidigJournalfoert") }
            validate { it.requireKey("fodselNrBruker") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("received packet from rapid: papirsøknad midelertidig journalført")
        Prometheus.papirsoeknadMottattCounter.inc()
    }
}
