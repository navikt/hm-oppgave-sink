package no.nav.hjelpemidler.oppgave

import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.configuration.LocalEnvironment
import no.nav.hjelpemidler.http.openid.azureADClient
import no.nav.hjelpemidler.oppgave.oppgave.OppgaveClient
import no.nav.hjelpemidler.oppgave.pdl.PdlClient
import no.nav.hjelpemidler.oppgave.service.OppgaveDataSink
import no.nav.hjelpemidler.oppgave.service.OpprettJournalføringsoppgaveEtterFeilregistreringAvSakstilknytning
import no.nav.hjelpemidler.oppgave.service.RutingOppgaveSink
import no.nav.hjelpemidler.oppgave.wiremock.WiremockServer
import kotlin.time.Duration.Companion.seconds

val jsonMapper = jacksonMapperBuilder().build()

fun main() {
    if (Environment.current == LocalEnvironment) {
        WiremockServer().startServer()
    }

    val azureAdClient = azureADClient {
        cache(leeway = 10.seconds) {
            maximumSize = 100
        }
    }

    val oppgaveClient = OppgaveClient(
        baseUrl = Configuration.OPPGAVE_BASE_URL,
        scope = Configuration.PROXY_SCOPE,
        azureAdClient = azureAdClient,
    )

    val pdlClient = PdlClient(
        baseUrl = Configuration.PDL_BASE_URL,
        scope = Configuration.PROXY_SCOPE,
        azureAdClient = azureAdClient,
    )

    RapidApplication
        .create(no.nav.hjelpemidler.configuration.Configuration.current)
        .apply {
            OppgaveDataSink(this, oppgaveClient, pdlClient)
            RutingOppgaveSink(this, oppgaveClient)
            OpprettJournalføringsoppgaveEtterFeilregistreringAvSakstilknytning(this, oppgaveClient, pdlClient)
        }
        .start()
}
