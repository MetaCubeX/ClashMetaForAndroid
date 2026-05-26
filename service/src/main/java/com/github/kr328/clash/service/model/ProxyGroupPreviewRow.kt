package com.github.kr328.clash.service.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

@Serializable
data class ProxyGroupPreviewRow(
    val type: Proxy.Type,
    val members: List<String>,
    /**
     * Mirrors mihomo's `hidden: true` flag from the YAML. Needed by the
     * picker's 1-hop heuristic: hidden groups only surface as pills when
     * they are direct members of a visible group's `proxies:` list.
     */
    val hidden: Boolean = false,
    /**
     * Static `proxies:` entries straight from the YAML — never includes
     * names expanded by `include-all-proxies` / `include-all-providers` /
     * `include-all`. The 1-hop heuristic uses this to tell a kaso-style
     * pure dispatch shell (static refs to hidden auto subgroups only)
     * from a normal group whose `members` happens to be a flat leaf list
     * because of dynamic expansion.
     */
    val staticProxies: List<String> = emptyList(),
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ProxyGroupPreviewRow> {
        override fun createFromParcel(parcel: Parcel): ProxyGroupPreviewRow {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<ProxyGroupPreviewRow?> {
            return arrayOfNulls(size)
        }
    }
}
