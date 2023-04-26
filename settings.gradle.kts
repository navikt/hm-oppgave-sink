rootProject.name = "hm-oppgave-sink"

sourceControl {
    gitRepository(uri("https://github.com/navikt/hm-http.git")) {
        producesModule("no.nav.hjelpemidler.http:hm-http")
    }
}
