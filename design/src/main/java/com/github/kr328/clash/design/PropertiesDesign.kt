package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.util.ValidatorsUnified
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class PropertiesDesign(context: Context) : Design<PropertiesDesign.Request>(context) {
    sealed class Request {
        object Commit : Request()
        object BrowseFiles : Request()
    }

    internal var profile by mutableStateOf<Profile?>(null)
    internal var progressing by mutableStateOf(false)
    internal var fetchStatusText by mutableStateOf("")
    internal var fetchProgress by mutableStateOf(0f)
    internal var fetchIndeterminate by mutableStateOf(true)

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.PropertiesScreen(this)
    }

    suspend fun withProcessing(executeTask: suspend (suspend (FetchStatus) -> Unit) -> Unit) {
        try {
            progressing = true
            fetchIndeterminate = true
            fetchStatusText = context.getString(R.string.initializing)

            executeTask { status ->
                withContext(Dispatchers.Main) {
                    when (status.action) {
                        FetchStatus.Action.FetchConfiguration -> {
                            fetchStatusText = context.getString(R.string.format_fetching_configuration, status.args[0])
                            fetchIndeterminate = true
                        }

                        FetchStatus.Action.FetchProviders -> {
                            fetchStatusText = context.getString(R.string.format_fetching_provider, status.args[0])
                            fetchIndeterminate = false
                            fetchProgress = if (status.max > 0) status.progress.toFloat() / status.max else 0f
                        }

                        FetchStatus.Action.Verifying -> {
                            fetchStatusText = context.getString(R.string.verifying)
                            fetchIndeterminate = false
                            fetchProgress = if (status.max > 0) status.progress.toFloat() / status.max else 0f
                        }
                    }
                }
            }
        } finally {
            progressing = false
        }
    }

    suspend fun requestExitWithoutSaving(): Boolean {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { ctx ->
                val dialog = MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.exit_without_save)
                    .setMessage(R.string.exit_without_save_warning)
                    .setCancelable(true)
                    .setPositiveButton(R.string.ok) { _, _ -> ctx.resume(true) }
                    .setNegativeButton(R.string.cancel) { _, _ -> }
                    .setOnDismissListener { if (!ctx.isCompleted) ctx.resume(false) }
                    .show()

                ctx.invokeOnCancellation { dialog.dismiss() }
            }
        }
    }

    fun inputName() {
        launch {
            val currentProfile = profile ?: return@launch
            val name = context.requestModelTextInput(
                initial = currentProfile.name,
                title = context.getText(R.string.name),
                hint = context.getText(R.string.properties),
                error = context.getText(R.string.should_not_be_blank),
                validator = { input -> !input.isBlank() }
            )

            if (name != currentProfile.name) {
                profile = currentProfile.copy(name = name)
            }
        }
    }

    fun inputUrl() {
        val currentProfile = profile ?: return
        if (currentProfile.type == Profile.Type.External) return

        launch {
            val url = context.requestModelTextInput(
                initial = currentProfile.source,
                title = context.getText(R.string.url),
                hint = context.getText(R.string.profile_url),
                error = context.getText(R.string.accept_http_content),
                validator = ValidatorsUnified::httpUrlLegacy
            )

            if (url != currentProfile.source) {
                profile = currentProfile.copy(source = url)
            }
        }
    }

    fun inputInterval() {
        launch {
            val currentProfile = profile ?: return@launch
            var minutes = TimeUnit.MILLISECONDS.toMinutes(currentProfile.interval)

            minutes = context.requestModelTextInput(
                initial = if (minutes == 0L) "" else minutes.toString(),
                title = context.getText(R.string.auto_update),
                hint = context.getText(R.string.auto_update_minutes),
                error = context.getText(R.string.at_least_15_minutes),
                validator = ValidatorsUnified::autoUpdateIntervalLegacy
            ).toLongOrNull() ?: 0

            val interval = TimeUnit.MINUTES.toMillis(minutes)

            if (interval != currentProfile.interval) {
                profile = currentProfile.copy(interval = interval)
            }
        }
    }

    fun requestCommit() {
        requests.trySend(Request.Commit)
    }

    fun requestBrowseFiles() {
        requests.trySend(Request.BrowseFiles)
    }
}
