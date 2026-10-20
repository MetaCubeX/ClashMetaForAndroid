package com.github.kr328.clash.design

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.design.dialog.ModelProgressBarConfigure
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.dialog.withModelProgressBar
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ios.PropertiesScreen
import com.github.kr328.clash.design.ios.iosConfirm
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.ValidatorAgeSecretKey
import com.github.kr328.clash.design.util.ValidatorAutoUpdateInterval
import com.github.kr328.clash.design.util.ValidatorHttpUrl
import com.github.kr328.clash.design.util.ValidatorNotBlank
import com.github.kr328.clash.design.util.isShareableSubscriptionUrl
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.github.kr328.clash.design.util.showSubscriptionQr
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PropertiesDesign(context: Context) : Design<PropertiesDesign.Request>(context) {
    sealed class Request {
        object Commit : Request()
        object BrowseFiles : Request()
    }

    private var insets by mutableStateOf(Insets.EMPTY)
    private var profileState by mutableStateOf<Profile?>(null)
    private var processing by mutableStateOf(false)

    var profile: Profile
        get() = profileState!!
        set(value) {
            profileState = value
        }

    val progressing: Boolean
        get() = processing

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val title = (context as? Activity)?.title?.toString()?.ifBlank { null }
            ?: context.getString(R.string.profile)
        setContent {
            ClashMetaXTheme {
                PropertiesScreen(
                    insets = insets,
                    title = title,
                    profile = profileState,
                    processing = processing,
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onSave = { requestCommit() },
                    onName = { inputName() },
                    onUrl = { inputUrl() },
                    onInterval = { inputInterval() },
                    onAgeKey = { inputAgeSecretKey() },
                    onShare = { shareSubscriptionQr() },
                    onBrowse = { requestBrowseFiles() },
                )
            }
        }
    }

    suspend fun withProcessing(executeTask: suspend (suspend (FetchStatus) -> Unit) -> Unit) {
        try {
            processing = true

            context.withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = context.getString(R.string.initializing)
                }

                executeTask {
                    configure {
                        applyFrom(it)
                    }
                }
            }
        } finally {
            processing = false
        }
    }

    suspend fun requestExitWithoutSaving(): Boolean {
        return withContext(Dispatchers.Main) {
            iosConfirm(
                context = context,
                title = context.getString(R.string.exit_without_save),
                message = context.getString(R.string.exit_without_save_warning),
                confirm = context.getString(R.string.ok),
                destructive = false,
            )
        }
    }

    fun inputName() {
        launch {
            val name = context.requestModelTextInput(
                initial = profile.name,
                title = context.getText(R.string.name),
                hint = context.getText(R.string.properties),
                error = context.getText(R.string.should_not_be_blank),
                validator = ValidatorNotBlank
            )

            if (name != profile.name) {
                profile = profile.copy(name = name)
            }
        }
    }

    fun canShareSubscriptionQr(profile: Profile): Boolean {
        return isShareableSubscriptionUrl(profile.source)
    }

    fun shareSubscriptionQr() {
        showSubscriptionQr(profile.name, profile.source)
    }

    fun inputUrl() {
        if (profile.type == Profile.Type.External)
            return

        launch {
            val url = context.requestModelTextInput(
                initial = profile.source,
                title = context.getText(R.string.url),
                hint = context.getText(R.string.profile_url),
                error = context.getText(R.string.accept_http_content),
                validator = ValidatorHttpUrl
            )

            if (url != profile.source) {
                profile = profile.copy(source = url)
            }
        }
    }

    fun inputAgeSecretKey() {
        launch {
            val ageSecretKey = context.requestModelTextInput(
                initial = profile.ageSecretKey ?: "",
                title = context.getText(R.string.age_secret_key),
                hint = context.getText(R.string.age_secret_key_hint),
                error = context.getText(R.string.age_secret_key_error),
                validator = ValidatorAgeSecretKey
            )

            val newKey = ageSecretKey.ifBlank { null }
            if (newKey != profile.ageSecretKey) {
                profile = profile.copy(ageSecretKey = newKey)
            }
        }
    }

    fun inputInterval() {
        launch {
            var minutes = TimeUnit.MILLISECONDS.toMinutes(profile.interval)

            minutes = context.requestModelTextInput(
                initial = if (minutes == 0L) "" else minutes.toString(),
                title = context.getText(R.string.auto_update),
                hint = context.getText(R.string.auto_update_minutes),
                error = context.getText(R.string.at_least_15_minutes),
                validator = ValidatorAutoUpdateInterval
            ).toLongOrNull() ?: 0

            val interval = TimeUnit.MINUTES.toMillis(minutes)

            if (interval != profile.interval) {
                profile = profile.copy(interval = interval)
            }
        }
    }

    fun requestCommit() {
        if (processing) return
        requests.trySend(Request.Commit)
    }

    fun requestBrowseFiles() {
        requests.trySend(Request.BrowseFiles)
    }

    private fun ModelProgressBarConfigure.applyFrom(status: FetchStatus) {
        when (status.action) {
            FetchStatus.Action.FetchConfiguration -> {
                text = context.getString(R.string.format_fetching_configuration, status.args[0])
                isIndeterminate = true
            }
            FetchStatus.Action.FetchProviders -> {
                text = context.getString(R.string.format_fetching_provider, status.args[0])
                isIndeterminate = false
                max = status.max
                progress = status.progress
            }
            FetchStatus.Action.SubscriptionInfo -> Unit
            FetchStatus.Action.Verifying -> {
                text = context.getString(R.string.verifying)
                isIndeterminate = false
                max = status.max
                progress = status.progress
            }
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
