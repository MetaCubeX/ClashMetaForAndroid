package com.github.kr328.clash.service.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Coverage for proxy-provider document generation/parsing and JSON label handling. */
class ProxyProvidersUiTest {

    // -- buildProxyProvidersDocument -------------------------------------

    @Test
    fun build_emptyRows_yieldsEmptyProxyProvidersBlock() {
        val doc = ProxyProvidersUi.buildProxyProvidersDocument(emptyList())
        assertTrue(doc.trimStart().startsWith("proxy-providers"))
    }

    @Test
    fun build_singleRow_hasNoAdditionalPrefix() {
        val doc = ProxyProvidersUi.buildProxyProvidersDocument(
            listOf(ProxyProviderUiRow("My VPN", "https://sub.example.com/a", 600)),
        )
        assertTrue(doc.contains("sub1"))
        assertTrue(doc.contains("https://sub.example.com/a"))
        assertTrue(doc.contains("health-check"))
        assertFalse("single provider must not get a name prefix", doc.contains("additional-prefix"))
    }

    @Test
    fun build_multipleRows_eachGetsUniqueAdditionalPrefix() {
        val doc = ProxyProvidersUi.buildProxyProvidersDocument(
            listOf(
                ProxyProviderUiRow("A", "https://a.example.com", 600),
                ProxyProviderUiRow("B", "https://b.example.com", 600),
            ),
        )
        assertTrue(doc.contains("additional-prefix"))
        assertTrue(doc.contains("[sub1] "))
        assertTrue(doc.contains("[sub2] "))
    }

    @Test
    fun build_coercesIntervalToMinimum() {
        val doc = ProxyProvidersUi.buildProxyProvidersDocument(
            listOf(ProxyProviderUiRow("A", "https://a.example.com", intervalSeconds = 10)),
        )
        // Round-trip the document so we read the provider interval (not the health-check one).
        val rows = ProxyProvidersUi.parseRows(doc, emptyMap())
        assertEquals(1, rows.size)
        assertEquals(60L, rows[0].intervalSeconds)
    }

    // -- parseRows round-trip --------------------------------------------

    @Test
    fun parseRows_roundTripsUrlsAndLabels() {
        val input = listOf(
            ProxyProviderUiRow("A", "https://a.example.com", 600),
            ProxyProviderUiRow("B", "https://b.example.com", 900),
        )
        val doc = ProxyProvidersUi.buildProxyProvidersDocument(input)
        val labels = mapOf("sub1" to "Alpha", "sub2" to "Beta")

        val rows = ProxyProvidersUi.parseRows(doc, labels)

        assertEquals(2, rows.size)
        assertEquals("https://a.example.com", rows[0].url)
        assertEquals("Alpha", rows[0].title)
        assertEquals("https://b.example.com", rows[1].url)
        assertEquals(900L, rows[1].intervalSeconds)
    }

    @Test
    fun parseRows_blankOrMalformed_yieldsEmpty() {
        assertTrue(ProxyProvidersUi.parseRows(null, emptyMap()).isEmpty())
        assertTrue(ProxyProvidersUi.parseRows("", emptyMap()).isEmpty())
        assertTrue(ProxyProvidersUi.parseRows("not: [valid", emptyMap()).isEmpty())
    }

    // -- buildLabels -----------------------------------------------------

    @Test
    fun buildLabels_skipsEmptyTitles() {
        val labels = ProxyProvidersUi.buildLabels(
            listOf(
                ProxyProviderUiRow("Named", "https://a", 600),
                ProxyProviderUiRow("  ", "https://b", 600),
            ),
        )
        assertEquals(mapOf("sub1" to "Named"), labels)
    }

    // -- parseLabelsJson / labelsToJson (real org.json) ------------------

    @Test
    fun parseLabelsJson_validNullAndMalformed() {
        assertEquals(
            mapOf("sub1" to "My VPN", "sub2" to "Work"),
            ProxyProvidersUi.parseLabelsJson("""{"sub1":"My VPN","sub2":"Work"}"""),
        )
        assertTrue(ProxyProvidersUi.parseLabelsJson(null).isEmpty())
        assertTrue(ProxyProvidersUi.parseLabelsJson("").isEmpty())
        assertTrue(ProxyProvidersUi.parseLabelsJson("not json").isEmpty())
    }

    @Test
    fun labelsJson_roundTrips() {
        val labels = mapOf("sub1" to "Alpha", "sub2" to "Beta")
        val json = ProxyProvidersUi.labelsToJson(labels)
        assertEquals(labels, ProxyProvidersUi.parseLabelsJson(json))
    }
}
