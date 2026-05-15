package com.github.kr328.clash.util

import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.model.Profile
import org.json.JSONObject
import java.util.UUID

/**
 * Persists subscription HTTP header snapshot per profile so switching the active profile
 * can show the correct announcement/support/userinfo before the next network refresh.
 */
object SubscriptionMetaCache {
    data class Entry(
        val announcement: String = "",
        val announcementUrl: String = "",
        val supportUrl: String = "",
        val userinfo: String = "",
    )

    fun get(store: UiStore, uuid: UUID?): Entry? {
        if (uuid == null) return null
        return runCatching {
            val root = JSONObject(store.subscriptionMetaCacheJson.takeIf { it.isNotBlank() } ?: "{}")
            if (!root.has(uuid.toString())) return null
            val o = root.getJSONObject(uuid.toString())
            Entry(
                announcement = o.optString("a", ""),
                announcementUrl = o.optString("au", ""),
                supportUrl = o.optString("s", ""),
                userinfo = o.optString("u", ""),
            )
        }.getOrNull()?.takeIf { it != Entry() }
    }

    fun put(store: UiStore, uuid: UUID, entry: Entry) {
        runCatching {
            val root = JSONObject(store.subscriptionMetaCacheJson.takeIf { it.isNotBlank() } ?: "{}")
            val o = JSONObject()
            o.put("a", entry.announcement)
            o.put("au", entry.announcementUrl)
            o.put("s", entry.supportUrl)
            o.put("u", entry.userinfo)
            root.put(uuid.toString(), o)
            store.subscriptionMetaCacheJson = root.toString()
        }
    }

    /**
     * Applies cached operator metadata to [UiStore] for the given profile when uploads
     * from the subscription server are allowed to overwrite globals.
     */
    fun hydrateUiStoreForProfile(store: UiStore, profile: Profile?) {
        if (profile == null || store.subscriptionMetadataLockUser) return
        val entry = get(store, profile.uuid)
        if (entry != null) {
            store.announcement = entry.announcement
            store.announcementUrl = entry.announcementUrl
            store.supportUrl = entry.supportUrl
            store.subscriptionUserinfo = entry.userinfo
        } else {
            store.announcement = ""
            store.announcementUrl = ""
            store.supportUrl = ""
            store.subscriptionUserinfo = ""
            store.profileWebPageUrl = ""
            store.profileUpdateIntervalHours = 0
            store.subscriptionHwidActive = ""
            store.subscriptionHwidNotSupported = ""
            store.subscriptionHwidMaxDevicesReached = ""
            store.subscriptionHwidLimit = ""
        }
    }
}
