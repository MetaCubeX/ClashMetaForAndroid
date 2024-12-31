package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.serialization.Serializable

@Serializable
data class Proxy(
    val name: String,
    val title: String,
    val subtitle: String,
    val type: Type,
    val delay: Int = 0 // Default value if applicable
) : Parcelable {
    enum class Type(val group: Boolean) {
        Direct(false),
        Reject(false),
        RejectDrop(false),
        Compatible(false),
        Pass(false),
        Shadowsocks(false),
        ShadowsocksR(false),
        Snell(false),
        Socks5(false),
        Http(false),
        Vmess(false),
        Vless(false),
        Trojan(false),
        Hysteria(false),
        Hysteria2(false),
        Tuic(false),
        WireGuard(false),
        Dns(false),
        Ssh(false),
        Mieru(false),
        Relay(true),
        Selector(true),
        Fallback(true),
        URLTest(true),
        LoadBalance(true),
        Unknown(false);
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(title)
        parcel.writeString(subtitle)
        parcel.writeString(type.name)
        parcel.writeInt(delay)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Proxy> {
        override fun createFromParcel(parcel: Parcel): Proxy {
            return Proxy(
                name = parcel.readString()!!,
                title = parcel.readString()!!,
                subtitle = parcel.readString()!!,
                type = Type.valueOf(parcel.readString()!!),
                delay = parcel.readInt()
            )
        }

        override fun newArray(size: Int): Array<Proxy?> = arrayOfNulls(size)
    }
}
