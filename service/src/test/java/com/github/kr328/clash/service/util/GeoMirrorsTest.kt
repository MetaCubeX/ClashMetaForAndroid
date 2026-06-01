package com.github.kr328.clash.service.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SEC-2: geox-url must be accepted only from trusted hosts (allowlist, fail-closed). */
class GeoMirrorsTest {

    // -- isTrusted -------------------------------------------------------

    @Test
    fun trustedMirrorHostsAreTrusted() {
        // Derived from the curated mirror lists.
        assertTrue(GeoMirrors.isTrusted("https://github.com/MetaCubeX/meta-rules-dat/releases/latest/download/geoip.dat"))
        assertTrue(GeoMirrors.isTrusted("https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/release/geoip.dat"))
        assertTrue(GeoMirrors.isTrusted("https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geoip.dat"))
        assertTrue(GeoMirrors.isTrusted("https://fastly.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geoip.dat"))
    }

    @Test
    fun arbitraryHostIsNotTrusted() {
        assertFalse(GeoMirrors.isTrusted("https://evil.example/geoip.dat"))
        // Previously denylisted GitHub proxy — now simply not on the allowlist.
        assertFalse(GeoMirrors.isTrusted("https://gh.zukijourney.com/MetaCubeX/meta-rules-dat/release/geoip.dat"))
    }

    @Test
    fun blankOrMalformedFailsClosed() {
        assertFalse(GeoMirrors.isTrusted(null))
        assertFalse(GeoMirrors.isTrusted(""))
        assertFalse(GeoMirrors.isTrusted("   "))
        assertFalse(GeoMirrors.isTrusted("not a url"))
        assertFalse(GeoMirrors.isTrusted("https://"))
    }

    @Test
    fun matchIsExactHostNotSubstring() {
        // Must not be fooled by look-alike hosts containing a trusted token.
        assertFalse(GeoMirrors.isTrusted("https://evilgithub.com/x/geoip.dat"))
        assertFalse(GeoMirrors.isTrusted("https://github.com.evil.com/x/geoip.dat"))
        assertFalse(GeoMirrors.isTrusted("https://attacker.com/?u=https://github.com/x"))
    }

    @Test
    fun trustedHostWithPortAndUserinfoStillTrusted() {
        assertTrue(GeoMirrors.isTrusted("https://github.com:443/x/geoip.dat"))
        assertTrue(GeoMirrors.isTrusted("https://user:pass@github.com/x/geoip.dat"))
        assertTrue(GeoMirrors.isTrusted("github.com/x/geoip.dat")) // no scheme
        assertTrue(GeoMirrors.isTrusted("HTTPS://GITHUB.COM/x")) // case-insensitive
    }

    // -- extractHost -----------------------------------------------------

    @Test
    fun extractHostHandlesCommonForms() {
        assertEquals("github.com", GeoMirrors.extractHost("https://github.com/a/b"))
        assertEquals("github.com", GeoMirrors.extractHost("github.com:443/a"))
        assertEquals("github.com", GeoMirrors.extractHost("https://user:pass@github.com:8443/a?x=1#y"))
        assertEquals("::1", GeoMirrors.extractHost("http://[::1]:9090/x"))
        assertNull(GeoMirrors.extractHost(null))
        assertNull(GeoMirrors.extractHost(""))
        assertNull(GeoMirrors.extractHost("https://"))
    }

    // -- sanitize (fail-closed) ------------------------------------------

    @Test
    fun sanitizeReplacesUntrustedWithPrimary() {
        assertEquals(
            GeoMirrors.primaryGeoIpDat(),
            GeoMirrors.sanitize("https://evil.example/geoip.dat", GeoMirrors.GeoKind.GeoIp),
        )
        assertEquals(
            GeoMirrors.primaryGeoSiteDat(),
            GeoMirrors.sanitize(null, GeoMirrors.GeoKind.GeoSite),
        )
        assertEquals(
            GeoMirrors.primaryGeoIpMmdb(),
            GeoMirrors.sanitize("garbage", GeoMirrors.GeoKind.GeoIpMmdb),
        )
    }

    @Test
    fun sanitizeKeepsTrustedUrl() {
        val trusted = "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/release/geoip.dat"
        assertEquals(trusted, GeoMirrors.sanitize(trusted, GeoMirrors.GeoKind.GeoIp))
    }
}
