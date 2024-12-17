package no.nav.hjelpemidler.oppgave.service

import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import no.nav.hjelpemidler.logging.secureLog

interface PacketListenerWithOnError : River.PacketListener {
    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        secureLog.info { "Feil i melding fra rapid: '${problems.toExtendedReport()}'" }
        error("Feil i melding fra rapid, se secureLog for detaljer")
    }
}
