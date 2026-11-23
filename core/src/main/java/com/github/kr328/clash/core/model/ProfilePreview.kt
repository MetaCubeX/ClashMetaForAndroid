package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

@Serializable
data class ProfilePreview(
    val groups: List<ProfilePreviewGroup> = emptyList(),
    val proxies: List<ProfilePreviewProxy> = emptyList(),
    val providers: List<ProfilePreviewProvider> = emptyList(),
    val error: String? = null,
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ProfilePreview> {
        override fun createFromParcel(parcel: Parcel): ProfilePreview {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<ProfilePreview?> = arrayOfNulls(size)
    }
}

@Serializable
data class ProfilePreviewGroup(
    val name: String,
    val type: String = "",
    val members: List<ProfilePreviewMember> = emptyList(),
    val includeAll: Boolean = false,
)

@Serializable
data class ProfilePreviewMember(
    val name: String,
    val type: String = "",
    val kind: String = "proxy",
)

@Serializable
data class ProfilePreviewProxy(
    val name: String,
    val type: String = "",
)

@Serializable
data class ProfilePreviewProvider(
    val name: String,
    val type: String = "",
)
