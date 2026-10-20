package com.github.kr328.clash.design

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.dialog.withModelProgressBar
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ios.WebdavSettingsScreen
import com.github.kr328.clash.design.ios.WebdavSettingsUiState
import com.github.kr328.clash.design.ios.iosConfirm
import com.github.kr328.clash.design.preference.NullableTextAdapter
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.github.kr328.clash.service.webdav.WebdavSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class WebdavSettingsDesign(
    context: Context,
    private val store: WebdavSettingsStore,
    status: String,
) : Design<WebdavSettingsDesign.Request>(context) {
    enum class Request {
        Test, Sync, Pull, Push,
    }

    private var insets by mutableStateOf(Insets.EMPTY)
    private var urlText by mutableStateOf(store.url)
    private var lanText by mutableStateOf(store.lanUrl)
    private var usernameText by mutableStateOf(store.username)
    private var passwordEmpty by mutableStateOf(store.password.isEmpty())
    private var remoteText by mutableStateOf(store.remotePath)
    private var autoSync by mutableStateOf(store.autoSync)
    private var statusText by mutableStateOf(
        status.ifBlank { context.getString(R.string.webdav_status_empty) },
    )
    private var busy = false
    private val autoSyncMutex = Mutex()
    private val autoSyncGeneration = AtomicLong()

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ClashMetaXTheme {
                WebdavSettingsScreen(
                    insets = insets,
                    title = context.pushedTitle(R.string.webdav_sync),
                    state = WebdavSettingsUiState(
                        url = urlText,
                        lanUrl = lanText,
                        username = usernameText,
                        passwordEmpty = passwordEmpty,
                        remotePath = remoteText,
                        autoSync = autoSync,
                        status = statusText,
                    ),
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onUrl = { onUrl() },
                    onLanUrl = { onLanUrl() },
                    onUsername = { onUsername() },
                    onPassword = { onPassword() },
                    onRemotePath = { onRemotePath() },
                    onAutoSync = { onAutoSync(it) },
                    onTest = { send(Request.Test) },
                    onSync = { confirmThenSend(R.string.webdav_sync_now, Request.Sync) },
                    onPull = { confirmThenSend(R.string.webdav_pull, Request.Pull) },
                    onPush = { confirmThenSend(R.string.webdav_push, Request.Push) },
                )
            }
        }
    }

    fun setStatus(text: String) {
        statusText = text.ifBlank { context.getString(R.string.webdav_status_empty) }
    }

    fun onActionFinished() {
        busy = false
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

    private fun onUrl() {
        edit(R.string.webdav_url, { store.url }, { store.url = it }) { urlText = it }
    }

    private fun onLanUrl() {
        edit(R.string.webdav_lan_url, { store.lanUrl }, { store.lanUrl = it }) { lanText = it }
    }

    private fun onUsername() {
        edit(R.string.webdav_username, { store.username }, { store.username = it }) { usernameText = it }
    }

    private fun onRemotePath() {
        edit(R.string.webdav_remote_path, { store.remotePath }, { store.remotePath = it }) { remoteText = it }
    }

    private fun onPassword() {
        launch(Dispatchers.Main) {
            val initial = withContext(Dispatchers.IO) { store.password }
            val entered = context.requestModelTextInput(
                initial = initial,
                title = context.getText(R.string.webdav_password),
                hint = context.getText(R.string.webdav_password),
            )
            passwordEmpty = withContext(Dispatchers.IO) {
                store.password = entered
                store.password.isEmpty()
            }
        }
    }

    private fun onAutoSync(enabled: Boolean) {
        val generation = autoSyncGeneration.incrementAndGet()
        autoSync = enabled
        launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                autoSyncMutex.withLock {
                    if (generation != autoSyncGeneration.get()) return@withLock
                    store.autoSync = enabled
                }
            }
            if (generation == autoSyncGeneration.get() && enabled) {
                if (requests.trySend(Request.Sync).isSuccess) busy = true
            }
        }
    }

    private fun confirmThenSend(@StringRes titleRes: Int, request: Request) {
        if (busy) return
        busy = true
        launch(Dispatchers.Main) {
            var sent = false
            try {
                val ok = iosConfirm(
                    context,
                    title = context.getString(titleRes),
                    message = context.getString(R.string.webdav_tips),
                    confirm = context.getString(R.string.ok),
                )
                if (ok) sent = requests.trySend(request).isSuccess
            } finally {
                if (!sent) busy = false
            }
        }
    }

    private fun send(request: Request) {
        if (busy) return
        busy = true
        if (!requests.trySend(request).isSuccess) busy = false
    }

    private fun edit(
        @StringRes titleRes: Int,
        read: () -> String?,
        write: (String?) -> Unit,
        show: (String?) -> Unit,
    ) {
        launch(Dispatchers.Main) {
            val initial = withContext(Dispatchers.IO) { read() }
            val text = context.requestModelTextInput(
                initial = initial,
                title = context.getText(titleRes),
                reset = context.getText(R.string.reset),
                hint = context.getText(titleRes),
            )
            val shown = withContext(Dispatchers.IO) {
                val newValue = NullableTextAdapter.String.to(text)
                write(newValue)
                NullableTextAdapter.String.from(newValue)
            }
            show(shown)
        }
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

    init {
        if (context is AppCompatActivity) {
            context.window.decorView.setOnInsertsChangedListener {
                if (surface.insets != it) surface.insets = it
                insets = it
            }
        }
    }
}

private fun Context.pushedTitle(fallback: Int): String {
    val current = (this as? Activity)?.title?.toString()?.ifBlank { null }
    return current ?: getString(fallback)
}
