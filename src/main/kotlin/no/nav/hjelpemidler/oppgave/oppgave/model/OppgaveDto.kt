package no.nav.hjelpemidler.oppgave.oppgave.model

data class OppgaveRequest(
    val aktoerId: String,
    val journalpostId: String,
    val beskrivelse: String,
    val temagruppe: String,
    val tema: String,
    val oppgavetype: String,
    val behandlingstype: String,
    val aktivDato: String,
    val fristFerdigstillelse: String,
    val prioritet: String
)

data class OppgaveResponse(
    val id: Int,
    val versjon: Int?,
    val tildeltEnhetsnr: String?,
    val journalpostId: String?,
    val aktoerId: String?,
    val beskrivelse: String?,
    val temagruppe: String?,
    val tema: String?,
    val oppgavetype: String?,
    val behandlingstype: String?,
    val fristFerdigstillelse: String?,
    val aktivDato: String?,
    val opprettetTidspunkt: String?,
    val opprettetAv: String?,
    val prioritet: String?,
    val status: String?
)
