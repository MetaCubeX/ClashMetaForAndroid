package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.GeoDataSourcePreset

data class GeoDataUrls(
    val geoIp: String,
    val geoSite: String,
    val mmdb: String,
    val asn: String,
)

/**
 * Presets only pick **download mirrors** for the same upstream pack
 * ([MetaCubeX/meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat) release track).
 * They are **not** separate “Russia-only” or “China-only” database builds.
 */
object GeoDataSources {
    private val global = GeoDataUrls(
        geoIp = "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geoip.dat",
        geoSite = "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geosite.dat",
        mmdb = "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geoip.metadb",
        asn = "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/GeoLite2-ASN.mmdb",
    )

    private val cnFriendly = GeoDataUrls(
        geoIp = "https://fastly.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geoip.dat",
        geoSite = "https://fastly.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geosite.dat",
        mmdb = "https://fastly.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geoip.metadb",
        asn = "https://fastly.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/GeoLite2-ASN.mmdb",
    )

    private val ruFriendly = GeoDataUrls(
        geoIp = "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/release/geoip.dat",
        geoSite = "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/release/geosite.dat",
        mmdb = "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/release/geoip.metadb",
        asn = "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/release/GeoLite2-ASN.mmdb",
    )

    fun defaults(preset: GeoDataSourcePreset): GeoDataUrls = when (preset) {
        GeoDataSourcePreset.Global -> global
        GeoDataSourcePreset.CnFriendly -> cnFriendly
        GeoDataSourcePreset.RuFriendly -> ruFriendly
        GeoDataSourcePreset.Custom -> global
    }

    fun resolve(
        preset: GeoDataSourcePreset,
        customGeoIp: String?,
        customGeoSite: String?,
        customMmdb: String?,
        customAsn: String?,
    ): GeoDataUrls {
        val fallback = defaults(if (preset == GeoDataSourcePreset.Custom) GeoDataSourcePreset.Global else preset)
        if (preset != GeoDataSourcePreset.Custom) return fallback

        fun pick(custom: String?, default: String): String {
            val value = custom?.trim().orEmpty()
            return if (value.isEmpty()) default else value
        }

        return GeoDataUrls(
            geoIp = pick(customGeoIp, fallback.geoIp),
            geoSite = pick(customGeoSite, fallback.geoSite),
            mmdb = pick(customMmdb, fallback.mmdb),
            asn = pick(customAsn, fallback.asn),
        )
    }
}
