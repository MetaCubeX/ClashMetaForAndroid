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
