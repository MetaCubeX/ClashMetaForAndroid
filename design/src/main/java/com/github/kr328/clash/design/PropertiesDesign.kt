package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.design.databinding.DesignPropertiesBinding
import com.github.kr328.clash.design.dialog.ModelProgressBarConfigure
import com.github.kr328.clash.design.dialog.withModelProgressBar
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
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
    private enum class UserAgentPreset {
        Default,
        HappIos,
        HappAndroid,
        V2RayTunIos,
        V2RayTunAndroid,
        Custom,
    }

    sealed class Request {
        object Commit : Request()
        object BrowseFiles : Request()
        object BrowseProxyProviders : Request()
    }

    private val binding = DesignPropertiesBinding
        .inflate(context.layoutInflater, context.root, false)

    private var suppressFieldSync: Boolean = false
    private var userAgentOverrideValue: String = ""
    private var userAgentChangeListener: ((String) -> Unit)? = null
    private var strictUserAgentValue: Boolean = false
    private var strictUserAgentChangeListener: ((Boolean) -> Unit)? = null
    private var userAgentPreset: UserAgentPreset = UserAgentPreset.Default

    /** Operator policy: disallow editing subscription URL (still allows name/interval). */
    var subscriptionSourceLocked: Boolean = false
        set(value) {
            field = value
            applyFieldEnabled()
        }

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

    var userAgentOverride: String
        get() = userAgentOverrideValue
        set(value) {
            val normalized = value.trim()
            userAgentOverrideValue = normalized
            userAgentPreset = when (normalized.lowercase()) {
                "" -> UserAgentPreset.Default
                "happ" -> UserAgentPreset.HappIos
                "happ/android" -> UserAgentPreset.HappAndroid
                "v2raytun/ios" -> UserAgentPreset.V2RayTunIos
                "v2raytun/android" -> UserAgentPreset.V2RayTunAndroid
                else -> UserAgentPreset.Custom
            }
            val presetLabel = presetLabel(userAgentPreset)
            if (binding.editUserAgentPreset.text?.toString() != presetLabel) {
                binding.editUserAgentPreset.setText(presetLabel, false)
            }
            if (userAgentPreset == UserAgentPreset.Custom &&
                binding.editUserAgentCustom.text?.toString() != normalized
            ) {
                binding.editUserAgentCustom.setText(normalized)
            }
            applyUserAgentVisibility()
        }

    fun setOnUserAgentChanged(listener: (String) -> Unit) {
        userAgentChangeListener = listener
    }

    var strictUserAgent: Boolean
        get() = strictUserAgentValue
        set(value) {
            strictUserAgentValue = value
            if (binding.switchUserAgentStrict.isChecked != value) {
                binding.switchUserAgentStrict.isChecked = value
            }
        }

    fun setOnStrictUserAgentChanged(listener: (Boolean) -> Unit) {
        strictUserAgentChangeListener = listener
    }

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

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)
        binding.editUserAgentPreset.setAdapter(
            ArrayAdapter(
                context,
                android.R.layout.simple_list_item_1,
                listOf(
                    presetLabel(UserAgentPreset.Default),
                    presetLabel(UserAgentPreset.HappIos),
                    presetLabel(UserAgentPreset.HappAndroid),
                    presetLabel(UserAgentPreset.V2RayTunIos),
                    presetLabel(UserAgentPreset.V2RayTunAndroid),
                    presetLabel(UserAgentPreset.Custom),
                ),
            ),
        )

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
        binding.editUserAgentPreset.setOnItemClickListener { _, _, position, _ ->
            userAgentPreset = when (position) {
                1 -> UserAgentPreset.HappIos
                2 -> UserAgentPreset.HappAndroid
                3 -> UserAgentPreset.V2RayTunIos
                4 -> UserAgentPreset.V2RayTunAndroid
                5 -> UserAgentPreset.Custom
                else -> UserAgentPreset.Default
            }
            applyUserAgentVisibility()
            val value = when (userAgentPreset) {
                UserAgentPreset.Default -> ""
                UserAgentPreset.HappIos -> "Happ"
                UserAgentPreset.HappAndroid -> "Happ/Android"
                UserAgentPreset.V2RayTunIos -> "v2raytun/ios"
                UserAgentPreset.V2RayTunAndroid -> "v2raytun/android"
                UserAgentPreset.Custom -> binding.editUserAgentCustom.text?.toString()?.trim().orEmpty()
            }
            if (userAgentOverrideValue != value) {
                userAgentOverrideValue = value
                userAgentChangeListener?.invoke(value)
            }
        }
        binding.editUserAgentCustom.doAfterTextChanged { text ->
            if (userAgentPreset != UserAgentPreset.Custom) return@doAfterTextChanged
            val value = text?.toString()?.trim().orEmpty()
            if (userAgentOverrideValue == value) return@doAfterTextChanged
            userAgentOverrideValue = value
            userAgentChangeListener?.invoke(value)
        }
        binding.switchUserAgentStrict.setOnCheckedChangeListener { _, checked ->
            if (strictUserAgentValue == checked) return@setOnCheckedChangeListener
            strictUserAgentValue = checked
            strictUserAgentChangeListener?.invoke(checked)
        }
        applyUserAgentVisibility()
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
        binding.layoutUrl.isEnabled = p.type == Profile.Type.Url && !subscriptionSourceLocked
        binding.layoutInterval.isEnabled = p.type != Profile.Type.File
    }

    private fun applyUserAgentVisibility() {
        binding.layoutUserAgentCustom.visibility =
            if (userAgentPreset == UserAgentPreset.Custom) View.VISIBLE else View.GONE
    }

    private fun presetLabel(preset: UserAgentPreset): String = when (preset) {
        UserAgentPreset.Default -> context.getString(R.string.subscription_user_agent_preset_default)
        UserAgentPreset.HappIos -> context.getString(R.string.subscription_user_agent_preset_happ_ios)
        UserAgentPreset.HappAndroid -> context.getString(R.string.subscription_user_agent_preset_happ_android)
        UserAgentPreset.V2RayTunIos -> context.getString(R.string.subscription_user_agent_preset_v2raytun_ios)
        UserAgentPreset.V2RayTunAndroid -> context.getString(R.string.subscription_user_agent_preset_v2raytun_android)
        UserAgentPreset.Custom -> context.getString(R.string.subscription_user_agent_preset_custom)
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

    fun focusSubscriptionUrlIfEmpty() {
        val p = profile
        if (p.type != Profile.Type.Url || p.source.isNotBlank()) return
        binding.editUrl.post {
            binding.editUrl.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return@post
            imm.showSoftInput(binding.editUrl, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun ModelProgressBarConfigure.applyFrom(status: FetchStatus) {
        when (status.action) {
            FetchStatus.Action.FetchConfiguration -> {
                text = context.getString(
                    R.string.format_fetching_configuration,
                    status.args.getOrElse(0) { "…" },
                )
                isIndeterminate = true
            }
            FetchStatus.Action.FetchProviders -> {
                text = context.getString(
                    R.string.format_fetching_provider,
                    status.args.getOrElse(0) { "…" },
                )
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
