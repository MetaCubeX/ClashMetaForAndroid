package com.github.kr328.clash.service.util

/**
 * Curated list of mirrors used to download GeoIP/GeoSite databases.
 *
 * The first entry is the preferred source. Mirrors are tried in order by the
 * underlying mihomo kernel only when the user has not explicitly set
 * `geox-url` in the profile. ClashFest seeds these defaults so that imported
 * subscriptions which omit `geox-url` (and rules referencing GEOIP/GEOSITE) do
 * not fail with `cant download geoip.dat`.
 */
object GeoMirrors {
    /** GeoIP `.dat` (mihomo binary format, used by `geodata-mode: true`). */
    val GEOIP_DAT: List<String> = listOf(
        "https://github.com/MetaCubeX/meta-rules-dat/releases/latest/download/geoip.dat",
        "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/release/geoip.dat",
        "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geoip.dat",
        "https://fastly.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geoip.dat",
        "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat",
    )

    /** GeoSite `.dat`. */
    val GEOSITE_DAT: List<String> = listOf(
        "https://github.com/MetaCubeX/meta-rules-dat/releases/latest/download/geosite.dat",
        "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/release/geosite.dat",
        "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geosite.dat",
        "https://fastly.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geosite.dat",
        "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat",
    )

    /** GeoIP MaxMind `.mmdb` (used when `geodata-mode: false`). */
    val GEOIP_MMDB: List<String> = listOf(
        "https://github.com/MetaCubeX/meta-rules-dat/releases/latest/download/country.mmdb",
        "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/release/country.mmdb",
        "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/country.mmdb",
    )

    /** Default URL chosen for [ConfigurationOverride.GeoXUrl.geoip]. */
    fun primaryGeoIpDat(): String = GEOIP_DAT.first()

    /** Default URL chosen for [ConfigurationOverride.GeoXUrl.geosite]. */
    fun primaryGeoSiteDat(): String = GEOSITE_DAT.first()

    /** Default URL chosen for [ConfigurationOverride.GeoXUrl.mmdb]. */
    fun primaryGeoIpMmdb(): String = GEOIP_MMDB.first()

    /**
     * Hostnames of public GitHub proxies that we observed to be unstable or
     * blocked: connections terminate with `io: read/write on closed pipe` or
     * timeout. When a profile's `geox-url` points at one of these we silently
     * rewrite it to a primary mirror so the import succeeds.
     */
    private val BROKEN_HOSTS: List<String> = listOf(
        "mirror.ghproxy.com",
        "ghproxy.com",
        "ghproxy.net",
        "ghproxy.io",
        "ghproxy.homeboyc.cn",
        "gh-proxy.com",
        "gh.api.99988866.xyz",
        "hub.gitmirror.com",
        "ghps.cc",
        "ghfast.top",
        "gh.zukijourney.com",
    )

    fun isBroken(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        // Match either as the URL host or as a "https://<broken>/<real-url>" prefix.
        return BROKEN_HOSTS.any { host ->
            lower.startsWith("https://$host/") ||
                lower.startsWith("http://$host/") ||
                lower.contains("://$host/")
        }
    }

    enum class GeoKind { GeoIp, GeoSite, GeoIpMmdb }

    /** Returns a known-good replacement for [url] when it points at a broken mirror. */
    fun sanitize(url: String?, kind: GeoKind): String? {
        if (!isBroken(url)) return url
        return when (kind) {
            GeoKind.GeoIp -> primaryGeoIpDat()
            GeoKind.GeoSite -> primaryGeoSiteDat()
            GeoKind.GeoIpMmdb -> primaryGeoIpMmdb()
        }
    }
}
