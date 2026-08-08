package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

@Serializable
data class Proxy(
    val name: String,
    val title: String,
    val subtitle: String,
    val type: String,
    val delay: Int,
    var isGroup: Boolean,
    val chain: List<String> = emptyList(),
    val server: String = "",
    val chainDetail: List<ProxyChainNode> = emptyList(),
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Proxy> {
        override fun createFromParcel(parcel: Parcel): Proxy {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<Proxy?> {
            return arrayOfNulls(size)
        }
    }
}

@Serializable
data class ProxyChainNode(
    val name: String,
    val type: String = "",
    val server: String = "",
    val details: List<ProxyDetail> = emptyList(),
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ProxyChainNode> {
        override fun createFromParcel(parcel: Parcel): ProxyChainNode {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<ProxyChainNode?> {
            return arrayOfNulls(size)
        }
    }
}

@Serializable
data class ProxyDetail(
    val label: String,
    val value: String,
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ProxyDetail> {
        override fun createFromParcel(parcel: Parcel): ProxyDetail {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<ProxyDetail?> {
            return arrayOfNulls(size)
        }
    }
}
