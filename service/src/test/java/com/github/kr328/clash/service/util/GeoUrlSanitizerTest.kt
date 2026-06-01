package com.github.kr328.clash.service.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SEC-2: hostile/untrusted geox-url hosts must be rewritten before the core sees them. */
class GeoUrlSanitizerTest {

    private fun parse(yaml: String): Map<String, Any?> =
        YamlFormatting.parseRootMap(yaml) ?: error("not a YAML map")

    @Suppress("UNCHECKED_CAST")
    private fun geox(yaml: String): Map<String, Any?> =
        parse(yaml)["geox-url"] as? Map<String, Any?> ?: error("no geox-url map")

    @Test
    fun untrustedGeoipIsRewrittenToPrimary() {
        val input = """
            geox-url:
              geoip: https://evil.example/geoip.dat
            geo-auto-update: false
        """.trimIndent()
        val out = GeoUrlSanitizer.sanitizeYaml(input)
        assertNotNull(out)
        assertEquals(GeoMirrors.primaryGeoIpDat(), geox(out!!)["geoip"])
    }

    @Test
    fun untrustedGeositeAndAsnRewritten() {
        val input = """
            geox-url:
              geosite: https://attacker.test/geosite.dat
              asn: http://1.2.3.4/asn.mmdb
            geo-auto-update: false
        """.trimIndent()
        val out = GeoUrlSanitizer.sanitizeYaml(input)!!
        assertEquals(GeoMirrors.primaryGeoSiteDat(), geox(out)["geosite"])
        assertEquals(GeoMirrors.primaryGeoIpMmdb(), geox(out)["asn"])
    }

    @Test
    fun trustedGeoxUrlIsLeftUnchanged() {
        val trusted = "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/release/geoip.dat"
        val input = """
            geox-url:
              geoip: $trusted
            geo-auto-update: false
        """.trimIndent()
        val out = GeoUrlSanitizer.sanitizeYaml(input)!!
        assertEquals(trusted, geox(out)["geoip"])
    }

    @Test
    fun blankHostFailsClosed() {
        val input = """
            geox-url:
              geoip: ""
            geo-auto-update: false
        """.trimIndent()
        val out = GeoUrlSanitizer.sanitizeYaml(input)!!
        assertEquals(GeoMirrors.primaryGeoIpDat(), geox(out)["geoip"])
    }

    @Test
    fun absentKeyIsNotAdded() {
        // Only geosite present — geoip/mmdb/asn must not be injected.
        val input = """
            geox-url:
              geosite: https://evil.example/geosite.dat
            geo-auto-update: false
        """.trimIndent()
        val out = GeoUrlSanitizer.sanitizeYaml(input)!!
        val map = geox(out)
        assertEquals(GeoMirrors.primaryGeoSiteDat(), map["geosite"])
        assertFalse("geoip must not be injected", map.containsKey("geoip"))
        assertFalse("mmdb must not be injected", map.containsKey("mmdb"))
    }

    @Test
    fun idempotentOnTrustedInput() {
        val input = """
            geox-url:
              geoip: ${GeoMirrors.primaryGeoIpDat()}
            geo-auto-update: false
        """.trimIndent()
        val first = GeoUrlSanitizer.sanitizeYaml(input)!!
        val second = GeoUrlSanitizer.sanitizeYaml(first)!!
        assertEquals(geox(first)["geoip"], geox(second)["geoip"])
        assertTrue(GeoMirrors.isTrusted(geox(second)["geoip"]?.toString()))
    }
}
