
# README
![build-deploy-dev](https://github.com/navikt/hm-oppgave-sink/workflows/Build%20and%20deploy/badge.svg)

App som lytter på rapid og oppretter oppgaver i GOSYS for arkiverte søknader.


# Lokal køyring

oppgave(GOSYS), PDL og AzureAd er mocka med Wiremock

- start [backend](https://github.com/navikt/hm-soknad-api) for å starte rapid og evt. populere rapid
- start [hm-soknadsbehandling](https://github.com/navikt/hm-soknadsbehandling) for å lagre søknad i db og sende videre på rapid
- start [hm-joark-sink](https://github.com/navikt/hm-joark-sink) for å arkivere søknad mot mock dokarkiv og sende videre på rapid

- start hm-oppgave-sink og vent på melding


# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #digihot-dev.
