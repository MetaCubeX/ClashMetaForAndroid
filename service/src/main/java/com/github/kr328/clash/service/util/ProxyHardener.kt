package com.github.kr328.clash.service.util

import android.os.Build
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.service.model.ProxyHardeningMode

/**
 * Applies ClashFest hardening against the local-listener leak (a.k.a.
 * VLESS-SOCKS5 vulnerability) and direct TUN access from other apps on the
 * same device.
 *
 * On Android 10+ and [ProxyHardeningMode.Strict] (default) this completely
 * disables the SOCKS / HTTP / Mixed / Redir / TProxy listeners by forcing
 * their port to `0`. The TUN tunnel remains the only ingress, which makes it
 * impossible for unrelated apps on the device to bypass the per-app routing
 * rules by talking to `127.0.0.1:7891`.
 *
 * In [ProxyHardeningMode.Compat] the listeners are kept open but bound to
 * `127.0.0.1` and gated by a randomly generated session credential, which is
 * already what [RuntimeSocksAuth] used to do. [ProxyHardeningMode.Off]
 * disables this layer altogether.
 */
object ProxyHardener {
    private const val DISABLED_PORT = 0

    /**
     * Apply the requested [mode] to [configuration].
     * @return true if [configuration] was mutated.
     */
    fun applyTo(
        configuration: ConfigurationOverride,
        mode: ProxyHardeningMode,
        seedGeoMirrors: Boolean,
    ): Boolean {
        var changed = false

        if (seedGeoMirrors) {
            changed = ensureGeoMirrors(configuration) || changed
        }

        when (mode) {
            ProxyHardeningMode.Off -> Unit
            ProxyHardeningMode.Compat -> {
                changed = RuntimeSocksAuth.applyTo(configuration) || changed
            }
            ProxyHardeningMode.Strict -> {
                changed = RuntimeSocksAuth.applyTo(configuration) || changed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    changed = disableLocalListeners(configuration) || changed
                }
            }
        }

        return changed
    }

    /**
     * Force every local proxy listener to be disabled. mihomo treats `0` as
     * "do not listen".
     */
    private fun disableLocalListeners(configuration: ConfigurationOverride): Boolean {
        var changed = false

        if (configuration.httpPort != DISABLED_PORT) {
            configuration.httpPort = DISABLED_PORT
            changed = true
        }
        if (configuration.socksPort != DISABLED_PORT) {
            configuration.socksPort = DISABLED_PORT
            changed = true
        }
        if (configuration.mixedPort != DISABLED_PORT) {
            configuration.mixedPort = DISABLED_PORT
            changed = true
        }
        if (configuration.redirectPort != DISABLED_PORT) {
            configuration.redirectPort = DISABLED_PORT
            changed = true
        }
        if (configuration.tproxyPort != DISABLED_PORT) {
            configuration.tproxyPort = DISABLED_PORT
            changed = true
        }

        if (changed) {
            Log.i("ProxyHardener: local listeners disabled (Strict mode)")
        }

        return changed
    }

    private fun ensureGeoMirrors(configuration: ConfigurationOverride): Boolean {
        var changed = false
        val geo = configuration.geoxurl

        if (geo.geoip.isNullOrBlank()) {
            geo.geoip = GeoMirrors.primaryGeoIpDat()
            changed = true
        } else if (GeoMirrors.isBroken(geo.geoip)) {
            geo.geoip = GeoMirrors.primaryGeoIpDat()
            changed = true
        }
        if (geo.geosite.isNullOrBlank()) {
            geo.geosite = GeoMirrors.primaryGeoSiteDat()
            changed = true
        } else if (GeoMirrors.isBroken(geo.geosite)) {
            geo.geosite = GeoMirrors.primaryGeoSiteDat()
            changed = true
        }
        if (geo.mmdb.isNullOrBlank()) {
            geo.mmdb = GeoMirrors.primaryGeoIpMmdb()
            changed = true
        } else if (GeoMirrors.isBroken(geo.mmdb)) {
            geo.mmdb = GeoMirrors.primaryGeoIpMmdb()
            changed = true
        }

        if (changed) {
            Log.i("ProxyHardener: seeded default geox-url mirrors")
        }

        return changed
    }
}
