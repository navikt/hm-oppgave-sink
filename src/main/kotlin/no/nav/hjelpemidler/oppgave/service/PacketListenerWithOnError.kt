package no.nav.hjelpemidler.oppgave.service

import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.oppgave.logging.secureLog

interface PacketListenerWithOnError : River.PacketListener {
    override fun onError(problems: MessageProblems, context: MessageContext) {
        secureLog.info { "Feil i melding fra rapid: ${problems.toExtendedReport()}" }
        error("Feil i melding fra rapid, se sikkerlogg for detaljer")
    }
}
