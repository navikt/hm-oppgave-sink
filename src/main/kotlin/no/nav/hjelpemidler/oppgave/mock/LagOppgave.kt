package no.nav.hjelpemidler.oppgave.mock

import no.nav.hjelpemidler.oppgave.client.models.Oppgave
import java.time.LocalDate
import kotlin.random.Random

fun lagOppgave(id: Long = Random.nextLong(1_000_000, 9_999_999)): Oppgave = Oppgave(
    id = id,
    tildeltEnhetsnr = "9999",
    tema = "HJE",
    oppgavetype = "JFR",
    versjon = -1,
    prioritet = Oppgave.Prioritet.NORM,
    status = Oppgave.Status.OPPRETTET,
    aktivDato = LocalDate.now(),
)
