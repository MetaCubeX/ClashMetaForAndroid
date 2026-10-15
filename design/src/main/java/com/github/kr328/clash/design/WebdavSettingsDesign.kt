package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.dialog.withModelProgressBar
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
        Test, Sync, Pull, Push,
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
                value = store::lanUrl,
                adapter = NullableTextAdapter.String,
                title = R.string.webdav_lan_url,
                placeholder = R.string.webdav_unset,
                empty = R.string.webdav_unset,
            )

            tips(R.string.webdav_lan_hint)

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

            clickable(
                title = R.string.webdav_push,
                icon = R.drawable.ic_baseline_cloud_upload,
            ) {
                clicked { requests.trySend(Request.Push) }
            }
        }

        binding.content.addView(screen.root)
        setStatus(status)
    }

    suspend fun withProgress(
        block: suspend (suspend (phase: String, current: Int, total: Int, label: String) -> Unit) -> String,
    ): String {
        var result = ""
        context.withModelProgressBar {
            configure {
                isIndeterminate = true
                text = context.getString(R.string.webdav_progress_connect)
            }
            result = block { phase, current, total, label ->
                configure {
                    text = formatWebdavProgress(phase, current, total, label)
                    if (total <= 0) {
                        isIndeterminate = true
                    } else {
                        isIndeterminate = false
                        max = total
                        progress = current.coerceIn(0, total)
                    }
                }
            }
        }
        return result
    }

    private fun formatWebdavProgress(phase: String, current: Int, total: Int, label: String): String {
        val name = label.substringAfterLast('/').removeSuffix(".json")
        return when (phase) {
            "list" -> context.getString(R.string.webdav_progress_list)
            "download" -> namedProgress(R.string.webdav_progress_download, R.string.webdav_progress_download_named, current, total, name)
            "apply" -> namedProgress(R.string.webdav_progress_apply, R.string.webdav_progress_apply_named, current, total, name)
            "upload" -> namedProgress(R.string.webdav_progress_upload, R.string.webdav_progress_upload_named, current, total, name)
            "delete" -> namedProgress(R.string.webdav_progress_delete, R.string.webdav_progress_delete_named, current, total, name)
            else -> context.getString(R.string.webdav_progress_connect)
        }
    }

    private fun namedProgress(plain: Int, named: Int, current: Int, total: Int, name: String): String {
        val count = total.coerceAtLeast(current)
        return if (name.isBlank()) {
            context.getString(plain, current, count)
        } else {
            context.getString(named, current, count, name)
        }
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
