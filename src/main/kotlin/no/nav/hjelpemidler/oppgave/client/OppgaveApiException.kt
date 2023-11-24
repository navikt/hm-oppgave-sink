package no.nav.hjelpemidler.oppgave.client

class OppgaveApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

fun oppgaveApiError(
    message: String,
    cause: Throwable? = null,
): Nothing = throw OppgaveApiException(message, cause)
