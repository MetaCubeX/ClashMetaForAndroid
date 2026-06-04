package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.service.model.ProxyHardeningMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for the runtime hardening dispatch. NOTE: Strict's local-listener disabling is gated on
 * `Build.VERSION.SDK_INT >= Q`, which is 0 under JVM unit tests, so the port=0 path is only
 * reachable via instrumentation — asserted here only as "not applied off-device".
 */
class ProxyHardenerTest {

    // -- geo mirror seeding (fail-closed allowlist) ----------------------

    @Test
    fun geoSeed_seedsTrustedMirrors_whenBlank() {
        val config = ConfigurationOverride()

        val changed = ProxyHardener.applyTo(config, ProxyHardeningMode.Off, seedGeoMirrors = true)

        assertTrue(changed)
        assertTrue(GeoMirrors.isTrusted(config.geoxurl.geoip!!))
        assertTrue(GeoMirrors.isTrusted(config.geoxurl.geosite!!))
        assertTrue(GeoMirrors.isTrusted(config.geoxurl.mmdb!!))
    }

    @Test
    fun geoSeed_rewritesUntrustedHostToTrusted() {
        val config = ConfigurationOverride().apply {
            geoxurl.geoip = "https://evil.example.com/geoip.dat"
            geoxurl.geosite = "https://evil.example.com/geosite.dat"
            geoxurl.mmdb = "https://evil.example.com/country.mmdb"
        }

        val changed = ProxyHardener.applyTo(config, ProxyHardeningMode.Off, seedGeoMirrors = true)

        assertTrue(changed)
        assertTrue(GeoMirrors.isTrusted(config.geoxurl.geoip!!))
        assertFalse(config.geoxurl.geoip!!.contains("evil.example.com"))
    }

    @Test
    fun geoSeed_noChange_whenAlreadyTrusted() {
        val config = ConfigurationOverride().apply {
            geoxurl.geoip = GeoMirrors.primaryGeoIpDat()
            geoxurl.geosite = GeoMirrors.primaryGeoSiteDat()
            geoxurl.mmdb = GeoMirrors.primaryGeoIpMmdb()
        }

        val changed = ProxyHardener.applyTo(config, ProxyHardeningMode.Off, seedGeoMirrors = true)

        assertFalse(changed)
    }

    // -- mode dispatch ---------------------------------------------------

    @Test
    fun off_doesNotMutate_whenNoSeed() {
        val config = ConfigurationOverride()

        val changed = ProxyHardener.applyTo(config, ProxyHardeningMode.Off, seedGeoMirrors = false)

        assertFalse(changed)
        assertNull(config.socksPort)
        assertNull(config.allowLan)
    }

    @Test
    fun compat_appliesSocksAuthHardening() {
        val config = ConfigurationOverride().apply { allowLan = true }

        val changed = ProxyHardener.applyTo(config, ProxyHardeningMode.Compat, seedGeoMirrors = false)

        assertTrue(changed)
        // RuntimeSocksAuth forces allow-lan off so the local listener is not LAN-reachable.
        assertEquals(false, config.allowLan)
    }

    @Test
    fun strict_appliesSocksAuth_butPortDisableIsSdkGatedOffDevice() {
        val config = ConfigurationOverride().apply {
            allowLan = true
            socksPort = 7891
        }

        val changed = ProxyHardener.applyTo(config, ProxyHardeningMode.Strict, seedGeoMirrors = false)

        assertTrue(changed)
        assertEquals(false, config.allowLan)
        // SDK_INT is 0 under JVM tests, so disableLocalListeners() does not run here.
        assertEquals(7891, config.socksPort)
    }
}
