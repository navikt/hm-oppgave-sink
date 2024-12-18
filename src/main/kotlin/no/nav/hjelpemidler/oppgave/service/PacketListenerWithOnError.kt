package no.nav.hjelpemidler.oppgave.service

import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.logging.secureLog

private val log = KotlinLogging.logger {}

interface PacketListenerWithOnError : River.PacketListener {
    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        val message = "Validering av melding feilet, se secureLog for detaljer"
        log.info { message }
        secureLog.info { "Validering av melding feilet: '${problems.toExtendedReport()}'" }
        error(message)
    }
}
