package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.core.widget.doAfterTextChanged
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.design.databinding.DesignPropertiesBinding
import com.github.kr328.clash.design.dialog.ModelProgressBarConfigure
import com.github.kr328.clash.design.dialog.withModelProgressBar
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.getHtml
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
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
        object BrowseProxyProviders : Request()
    }

    private val binding = DesignPropertiesBinding
        .inflate(context.layoutInflater, context.root, false)

    private var suppressFieldSync: Boolean = false

    override val root: View
        get() = binding.root

    var profile: Profile
        get() = binding.profile!!
        set(value) {
            binding.profile = value
            syncFieldsFromProfile()
            applyFieldEnabled()
        }

    val progressing: Boolean
        get() = binding.processing

    suspend fun withProcessing(executeTask: suspend (suspend (FetchStatus) -> Unit) -> Unit) {
        try {
            binding.processing = true

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
            binding.processing = false
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

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.tips.text = context.getHtml(R.string.tips_properties)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        binding.editName.doAfterTextChanged { text ->
            if (suppressFieldSync) return@doAfterTextChanged
            profile = profile.copy(name = text?.toString().orEmpty())
        }
        binding.editUrl.doAfterTextChanged { text ->
            if (suppressFieldSync) return@doAfterTextChanged
            profile = profile.copy(source = text?.toString().orEmpty())
        }
        binding.editInterval.doAfterTextChanged { text ->
            if (suppressFieldSync) return@doAfterTextChanged
            val raw = text?.toString()?.trim().orEmpty()
            val minutes = raw.toLongOrNull() ?: 0L
            val interval = TimeUnit.MINUTES.toMillis(minutes.coerceAtLeast(0))
            profile = profile.copy(interval = interval)
        }
    }

    private fun syncFieldsFromProfile() {
        val p = profile
        suppressFieldSync = true
        try {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(p.interval)
            val intervalText = if (minutes == 0L) "" else minutes.toString()
            // Avoid setText when unchanged — setText resets the cursor and breaks inline rename (issue #2).
            if (binding.editName.text?.toString() != p.name) binding.editName.setText(p.name)
            if (binding.editUrl.text?.toString() != p.source) binding.editUrl.setText(p.source)
            if (binding.editInterval.text?.toString() != intervalText) binding.editInterval.setText(intervalText)
        } finally {
            suppressFieldSync = false
        }
    }

    private fun applyFieldEnabled() {
        val p = profile
        binding.layoutUrl.isEnabled = p.type == Profile.Type.Url
        binding.layoutInterval.isEnabled = p.type != Profile.Type.File
    }

    fun requestCommit() {
        requests.trySend(Request.Commit)
    }

    fun requestBrowseFiles() {
        requests.trySend(Request.BrowseFiles)
    }

    fun requestProxyProviders() {
        requests.trySend(Request.BrowseProxyProviders)
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
            FetchStatus.Action.Verifying -> {
                text = context.getString(R.string.verifying)
                isIndeterminate = false
                max = status.max
                progress = status.progress
            }
        }
    }
}
