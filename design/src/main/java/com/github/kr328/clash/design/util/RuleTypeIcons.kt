package com.github.kr328.clash.design.util

import androidx.annotation.DrawableRes
import com.github.kr328.clash.design.R

object RuleTypeIcons {
    @DrawableRes
    fun forMihomoType(type: String): Int = when (type.trim().uppercase()) {
        "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN", "DOMAIN-REGEX" -> R.drawable.ic_baseline_domain
        "IP-CIDR", "IP-CIDR6", "SRC-IP-CIDR", "IP-SUFFIX" -> R.drawable.ic_baseline_dns
        "GEOIP", "GEOSITE", "IP-ASN" -> R.drawable.ic_baseline_language
        "DST-PORT", "SRC-PORT", "IN-PORT" -> R.drawable.ic_baseline_bolt
        "PROCESS-NAME", "PROCESS-PATH", "PROCESS-NAME-REGEX" -> R.drawable.ic_baseline_get_app
        "NETWORK", "IN-TYPE" -> R.drawable.ic_baseline_route
        "MATCH" -> R.drawable.ic_baseline_filter_list
        else -> R.drawable.ic_baseline_extension
    }
}
