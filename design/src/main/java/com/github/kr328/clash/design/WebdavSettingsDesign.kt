package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.preference.ClickablePreference
import com.github.kr328.clash.design.preference.NullableTextAdapter
import com.github.kr328.clash.design.preference.OnChangedListener
import com.github.kr328.clash.design.preference.TipsPreference
import com.github.kr328.clash.design.preference.category
import com.github.kr328.clash.design.preference.clickable
import com.github.kr328.clash.design.preference.editableText
import com.github.kr328.clash.design.preference.preferenceScreen
import com.github.kr328.clash.design.preference.switch
import com.github.kr328.clash.design.preference.tips
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.webdav.WebdavSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebdavSettingsDesign(
    context: Context,
    private val store: WebdavSettingsStore,
    status: String,
) : Design<WebdavSettingsDesign.Request>(context) {
    enum class Request {
        Test, Sync, Pull,
    }

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    private var statusPreference: TipsPreference? = null
    private var passwordPreference: ClickablePreference? = null

    override val root: View
        get() = binding.root

    init {
        binding.surface = surface
        binding.activityBarLayout.applyFrom(context)
        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        val screen = preferenceScreen(context) {
            tips(R.string.webdav_tips)

            statusPreference = tips(R.string.webdav_status_empty)

            category(R.string.webdav_server)

            editableText(
                value = store::url,
                adapter = NullableTextAdapter.String,
                title = R.string.webdav_url,
                placeholder = R.string.webdav_url_placeholder,
                empty = R.string.webdav_url_placeholder,
            )

            editableText(
                value = store::username,
                adapter = NullableTextAdapter.String,
                title = R.string.webdav_username,
                empty = R.string.webdav_unset,
            )

            passwordPreference = clickable(
                title = R.string.webdav_password,
                summary = if (store.password.isEmpty()) R.string.webdav_unset else R.string.webdav_password_set,
            ) {
                clicked {
                    launch(Dispatchers.Main) {
                        val entered = context.requestModelTextInput(
                            initial = store.password,
                            title = context.getText(R.string.webdav_password),
                            hint = context.getText(R.string.webdav_password),
                        )
                        store.password = entered
                        refreshPassword()
                    }
                }
            }

            editableText(
                value = store::remotePath,
                adapter = NullableTextAdapter.String,
                title = R.string.webdav_remote_path,
                placeholder = R.string.webdav_remote_path_default,
                empty = R.string.webdav_remote_path_default,
            )

            category(R.string.webdav_sync)

            switch(
                value = store::autoSync,
                title = R.string.webdav_auto_sync,
                summary = R.string.webdav_auto_sync_summary,
            ) {
                listener = OnChangedListener {
                    if (store.autoSync) {
                        requests.trySend(Request.Sync)
                    }
                }
            }

            clickable(
                title = R.string.webdav_test,
                icon = R.drawable.ic_baseline_info,
            ) {
                clicked { requests.trySend(Request.Test) }
            }

            clickable(
                title = R.string.webdav_sync_now,
                icon = R.drawable.ic_baseline_sync,
            ) {
                clicked { requests.trySend(Request.Sync) }
            }

            clickable(
                title = R.string.webdav_pull,
                icon = R.drawable.ic_baseline_cloud_download,
            ) {
                clicked { requests.trySend(Request.Pull) }
            }
        }

        binding.content.addView(screen.root)
        setStatus(status)
    }

    fun setStatus(text: String) {
        statusPreference?.text = text.ifBlank { context.getString(R.string.webdav_status_empty) }
    }

    private fun refreshPassword() {
        passwordPreference?.summary = context.getString(
            if (store.password.isEmpty()) R.string.webdav_unset else R.string.webdav_password_set,
        )
    }
}
