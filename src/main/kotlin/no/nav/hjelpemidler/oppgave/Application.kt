package no.nav.hjelpemidler.oppgave

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.oppgave.oppgave.OppgaveClient
import no.nav.hjelpemidler.oppgave.pdl.PdlClient
import no.nav.hjelpemidler.oppgave.service.OppgaveDataSink
import no.nav.hjelpemidler.oppgave.service.OpprettJournalføringsoppgaveEtterFeilregistreringAvSakstilknytning
import no.nav.hjelpemidler.oppgave.service.RutingOppgaveSink
import no.nav.hjelpemidler.oppgave.wiremock.WiremockServer

fun main() {

    if (Configuration.application.profile == Profile.LOCAL) {
        WiremockServer(Configuration).startServer()
    }

    val azureClient = AzureClient(
        tenantUrl = "${Configuration.azure.tenantBaseUrl}/${Configuration.azure.tenantId}",
        clientId = Configuration.azure.clientId,
        clientSecret = Configuration.azure.clientSecret
    )
    val oppgaveClient = OppgaveClient(
        baseUrl = Configuration.oppgave.baseUrl,
        accesstokenScope = Configuration.azure.proxyScope,
        azureClient = azureClient
    )

    /*val oppgaveClientV2 = OppgaveClientV2(
        baseUrl = Configuration.oppgave.baseUrl,
        accesstokenScope = Configuration.azure.proxyScope,
        azureClient = azureClient
    )*/

    val pdlClient = PdlClient(
        baseUrl = Configuration.pdl.baseUrl,
        accesstokenScope = Configuration.azure.proxyScope,
        azureClient = azureClient
    )

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.rapidApplication))
        .build().apply {
            OppgaveDataSink(this, oppgaveClient, pdlClient)
            RutingOppgaveSink(this, oppgaveClient)
            OpprettJournalføringsoppgaveEtterFeilregistreringAvSakstilknytning(this, oppgaveClient, pdlClient)
        }.start()
}
