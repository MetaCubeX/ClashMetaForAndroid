package com.github.kr328.clash.design.branding

import com.github.kr328.clash.common.branding.BrandManifest

/**
 * Snapshot of the active operator brand pushed into the UI layer.
 *
 * Treated as immutable value object — the app layer rebuilds and re-pushes a
 * new [BrandHolder] whenever the persistent store changes. UI binders never
 * mutate this; they only read it and react.
 */
data class BrandHolder(
    val manifest: BrandManifest,
    /** Absolute path to the appropriate logo bitmap for the current theme, or null. */
    val logoPath: String?,
) {
    val isActive: Boolean get() = !manifest.isEmpty()

    companion object {
        val EMPTY = BrandHolder(BrandManifest.EMPTY, null)
    }
}
