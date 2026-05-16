package com.github.kr328.clash.service.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

@Serializable
data class ProxyTransportInfo(
    val network: String = "",
    val tls: Boolean = false,
    val reality: Boolean = false,
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ProxyTransportInfo> {
        override fun createFromParcel(parcel: Parcel): ProxyTransportInfo {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<ProxyTransportInfo?> {
            return arrayOfNulls(size)
        }
    }
}
