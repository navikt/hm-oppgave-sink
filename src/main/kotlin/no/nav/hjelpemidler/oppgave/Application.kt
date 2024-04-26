package no.nav.hjelpemidler.oppgave

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.http.openid.azureADClient
import no.nav.hjelpemidler.oppgave.client.OppgaveClient
import no.nav.hjelpemidler.oppgave.mock.MockServer
import no.nav.hjelpemidler.oppgave.service.OppgaveDataSink
import no.nav.hjelpemidler.oppgave.service.OpprettJournalføringsoppgaveEtterFeilregistreringAvSakstilknytning
import no.nav.hjelpemidler.oppgave.service.RutingOppgaveSink
import kotlin.time.Duration.Companion.seconds

fun main() {
    if (Environment.current.tier.isLocal) {
        MockServer().apply {
            setup()
            start()
        }
    }

    val azureAdClient = azureADClient {
        cache(leeway = 10.seconds) {
            maximumSize = 100
        }
    }

    val oppgaveClient = OppgaveClient(
        baseUrl = Configuration.OPPGAVE_BASE_URL,
        scope = Configuration.OPPGAVE_SCOPE,
        azureAdClient = azureAdClient,
    )

    RapidApplication
        .create(no.nav.hjelpemidler.configuration.Configuration.current)
        .apply {
            OppgaveDataSink(this, oppgaveClient)
            RutingOppgaveSink(this, oppgaveClient)
            OpprettJournalføringsoppgaveEtterFeilregistreringAvSakstilknytning(this, oppgaveClient)
        }
        .start()
}
