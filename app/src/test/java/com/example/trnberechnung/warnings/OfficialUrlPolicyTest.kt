package com.example.trnberechnung.warnings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OfficialUrlPolicyTest {
    @Test
    fun `only exact official https hosts are allowed`() {
        assertTrue(OfficialUrlPolicy.isAllowed("https://www2.bsh.de/aktdat/nwn/nwn-nord.pdf"))
        assertTrue(OfficialUrlPolicy.isAllowed("https://www.elwis.de/DE/dynamisch/Bfs/"))
        assertTrue(OfficialUrlPolicy.isAllowed("https://bsh.de/DE/startseite/startseite_node.html"))

        assertFalse(OfficialUrlPolicy.isAllowed("http://www.elwis.de/DE/dynamisch/Bfs/"))
        assertFalse(OfficialUrlPolicy.isAllowed("https://www.elwis.de.evil.example/DE/dynamisch/Bfs/"))
        assertFalse(OfficialUrlPolicy.isAllowed("https://evil.example/?next=https://www.elwis.de"))
        assertFalse(OfficialUrlPolicy.isAllowed("https://user:password@www.elwis.de/DE/dynamisch/Bfs/"))
        assertFalse(OfficialUrlPolicy.isAllowed("https://www.elwis.de:444/DE/dynamisch/Bfs/"))
    }

    @Test
    fun `pdf detection uses the validated path and not a query parameter`() {
        assertEquals(
            OfficialLinkKind.PDF,
            OfficialUrlPolicy.linkKind("https://www2.bsh.de/aktdat/nwn/NWN-NORD.PDF?download=1"),
        )
        assertEquals(
            OfficialLinkKind.WEB,
            OfficialUrlPolicy.linkKind("https://www.elwis.de/DE/dynamisch/Bfs/?file=meldung.pdf"),
        )
        assertNull(OfficialUrlPolicy.linkKind("https://example.org/amtlich.pdf"))
    }
}
