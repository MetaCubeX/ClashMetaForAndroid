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
     * Allowlist of trusted hosts, derived from the curated mirror lists above.
     *
     * AGENTS.md §4 requires geo downloads only from whitelisted mirrors, so any
     * `geox-url` whose host is not in this set is treated as untrusted and
     * rewritten to a trusted primary (fail-closed) — see [GeoUrlSanitizer] and
     * [ProxyHardener]. This replaces the previous denylist of broken GitHub
     * proxies: a host being absent from a denylist must never imply trust.
     *
     * Note (DOC-1): AGENTS.md §4 also lists public GitHub proxies (e.g.
     * `gh.zukijourney.com`) as fallbacks. Those were observed unstable and are
     * intentionally NOT trusted here; AGENTS.md §4 should be reconciled in a
     * separate rules PR.
     */
    val TRUSTED_HOSTS: Set<String> =
        (GEOIP_DAT + GEOSITE_DAT + GEOIP_MMDB).mapNotNull(::extractHost).toSet()

    enum class GeoKind { GeoIp, GeoSite, GeoIpMmdb }

    /** Primary trusted mirror for [kind]. */
    fun primaryFor(kind: GeoKind): String = when (kind) {
        GeoKind.GeoIp -> primaryGeoIpDat()
        GeoKind.GeoSite -> primaryGeoSiteDat()
        GeoKind.GeoIpMmdb -> primaryGeoIpMmdb()
    }

    /**
     * True only when [url]'s host is in [TRUSTED_HOSTS]. Fail-closed: blank,
     * unparseable, or hostless values are never trusted.
     */
    fun isTrusted(url: String?): Boolean {
        val host = extractHost(url) ?: return false
        return host in TRUSTED_HOSTS
    }

    /**
     * Returns [url] when it is trusted; otherwise the trusted primary for
     * [kind]. Untrusted / blank / malformed input always fails closed to a
     * trusted mirror.
     */
    fun sanitize(url: String?, kind: GeoKind): String =
        if (isTrusted(url)) url!! else primaryFor(kind)

    /**
     * Extracts the lowercased host from [url], tolerating a missing scheme,
     * userinfo, port, path/query/fragment, and IPv6 brackets. Returns null
     * when no host can be determined (caller treats null as untrusted).
     */
    fun extractHost(url: String?): String? {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return null
        var s = raw.substringAfter("://", raw)             // strip scheme if present
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@')                      // strip userinfo
        if (s.isEmpty()) return null
        if (s.startsWith("[")) {                           // IPv6 literal, e.g. [::1]:443
            val end = s.indexOf(']')
            if (end <= 1) return null
            return s.substring(1, end).lowercase()
        }
        return s.substringBefore(':').lowercase().ifEmpty { null }
    }
}
