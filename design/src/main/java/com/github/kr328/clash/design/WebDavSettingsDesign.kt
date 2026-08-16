package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.preference.NullableTextAdapter
import com.github.kr328.clash.design.preference.category
import com.github.kr328.clash.design.preference.clickable
import com.github.kr328.clash.design.preference.editableText
import com.github.kr328.clash.design.preference.preferenceScreen
import com.github.kr328.clash.design.preference.tips
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class WebDavSettingsDesign(
    context: Context,
    uiStore: UiStore,
) : Design<WebDavSettingsDesign.Request>(context) {
    enum class Request {
        SyncNow
    }

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.surface = surface

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        val screen = preferenceScreen(context) {
            tips(R.string.webdav_tips)

            category(R.string.webdav_server)

            editableText(
                value = uiStore::webdavUrl,
                adapter = NON_NULL_STRING,
                title = R.string.webdav_server_url,
                icon = R.drawable.ic_baseline_dns,
                placeholder = R.string.webdav_not_configured,
            )

            editableText(
                value = uiStore::webdavUsername,
                adapter = NON_NULL_STRING,
                title = R.string.webdav_username,
                icon = R.drawable.ic_baseline_info,
                placeholder = R.string.webdav_not_configured,
            )

            editableText(
                value = uiStore::webdavPassword,
                adapter = NON_NULL_STRING,
                title = R.string.webdav_password,
                icon = R.drawable.ic_baseline_key,
                placeholder = R.string.webdav_not_configured,
            )

            category(R.string.webdav_actions)

            clickable(
                title = R.string.webdav_pull_sync,
                icon = R.drawable.ic_baseline_cloud_download,
                summary = R.string.webdav_pull_sync_summary,
            ) {
                clicked {
                    requests.trySend(Request.SyncNow)
                }
            }
        }

        binding.content.addView(screen.root)
    }

    companion object {
        // editableText needs a NullableTextAdapter<T> matching the (non-null String) property.
        // Show an empty value as the placeholder by mapping "" -> null on the way out.
        private val NON_NULL_STRING = object : NullableTextAdapter<String> {
            override fun from(value: String): String? = value.ifEmpty { null }
            override fun to(text: String?): String = text?.trim() ?: ""
        }
    }
}
