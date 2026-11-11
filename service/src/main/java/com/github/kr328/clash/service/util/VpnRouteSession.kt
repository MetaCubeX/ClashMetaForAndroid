package com.github.kr328.clash.service.util

/** Used by the serial configuration load loop; commit state only after Builder application succeeds. */
class VpnRouteSession(private val apply: (List<String>) -> Unit) {
    private var applied: List<String>? = null

    fun update(exclusions: List<String>) {
        if (applied == exclusions) return
        apply(exclusions)
        applied = exclusions.toList()
    }
}
