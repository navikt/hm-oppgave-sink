package no.nav.hjelpemidler.oppgave.service

import com.fasterxml.jackson.databind.node.NullNode
import no.nav.hjelpemidler.oppgave.domain.Sakstype
import no.nav.hjelpemidler.oppgave.serialization.jsonMapper
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpprettOppgaveForOverføringTest {

    @Test
    fun `skal tolke NullNode riktig ved utleding av om saken haster`() {
        val jsonString = """{"soknad":{"hast": null}}"""
        val json = jsonMapper.readTree(jsonString)

        val hastNode = json["soknad"]?.get("hast")
        assertTrue(hastNode is NullNode)

        val erHast = OpprettetMottattJournalpost(
            journalpostId = "",
            fnrBruker = "",
            søknadId = UUID.randomUUID(),
            sakId = "",
            sakstype = Sakstype.SØKNAD,
            enhet = "",
            navIdent = null,
            valgteÅrsaker = null,
            begrunnelse = null,
            søknadJson = json,
        ).erHast
        assertFalse(erHast)
    }
}
