package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.widget.doOnTextChanged
import com.github.kr328.clash.design.databinding.DialogAppsStrategyConfigEditBinding
import com.github.kr328.clash.design.ios.AppsStrategyScreen
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.Validator
import com.github.kr328.clash.design.util.ValidatorAcceptAll
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.requestTextInput
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppsStrategyConfig
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume

class AppsStrategyDesign(context: Context) : Design<AppsStrategyDesign.Request>(context) {

    sealed class Request {
        object Create : Request()
        data class Edit(val config: AppsStrategyConfig) : Request()
        data class Active(val config: AppsStrategyConfig) : Request()
        data class Choose(val config: AppsStrategyConfig) : Request()
    }

    var configs by mutableStateOf<List<AppsStrategyConfig>>(emptyList())
        private set

    var activeUuid by mutableStateOf<UUID?>(null)
        private set

    private var insets by mutableStateOf(Insets.EMPTY)

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ClashMetaXTheme {
                AppsStrategyScreen(
                    insets = insets,
                    title = context.getString(R.string.apps_strategy),
                    configs = configs,
                    activeUuid = activeUuid,
                    onBack = {
                        (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
                    },
                    onCreate = { requestCreate() },
                    onActive = { request(Request.Active(it)) },
                    onChoose = { request(Request.Choose(it)) },
                    onEdit = { request(Request.Edit(it)) },
                )
            }
        }
    }

    suspend fun patchConfigs(configs: List<AppsStrategyConfig>, activeUuid: UUID?) {
        withContext(Dispatchers.Main) {
            this@AppsStrategyDesign.configs = configs
            this@AppsStrategyDesign.activeUuid = activeUuid
        }
    }

    fun requestCreate() {
        requests.trySend(Request.Create)
    }

    private fun request(request: Request) {
        requests.trySend(request)
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

data class AppListsConfigEditResult(
    val name: String,
    val mode: AccessControlMode,
    val deleted: Boolean = false
)

suspend fun Context.requestAppListsConfigEdit(
    initialName: String,
    initialMode: AccessControlMode,
    title: CharSequence,
    hint: CharSequence? = null,
    error: CharSequence? = null,
    showDelete: Boolean = false,
    validator: Validator = ValidatorAcceptAll,
): AppListsConfigEditResult? {
    return suspendCancellableCoroutine {
        val binding = DialogAppsStrategyConfigEditBinding
            .inflate(layoutInflater, this.root, false)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(binding.root)
            .setCancelable(true)
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = binding.textField.text?.toString() ?: ""
                val mode = when (binding.modeGroup.checkedRadioButtonId) {
                    R.id.mode_accept_selected -> AccessControlMode.AcceptSelected
                    R.id.mode_deny_selected -> AccessControlMode.DenySelected
                    else -> AccessControlMode.AcceptAll
                }

                if (validator(text))
                    it.resume(AppListsConfigEditResult(text, mode, false))
                else
                    it.resume(null)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .setOnDismissListener { _ ->
                if (!it.isCompleted)
                    it.resume(null)
            }

        if (showDelete) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                it.resume(AppListsConfigEditResult("", AccessControlMode.AcceptAll, true))
            }
        }

        val dialog = builder.create()

        it.invokeOnCancellation {
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            if (hint != null)
                binding.textLayout.hint = hint

            binding.textField.apply {
                binding.textLayout.isErrorEnabled = error != null

                doOnTextChanged { text, _, _, _ ->
                    if (!validator(text?.toString() ?: "")) {
                        if (error != null)
                            binding.textLayout.error = error

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    } else {
                        if (error != null)
                            binding.textLayout.error = null

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    }
                }

                setText(initialName)
                setSelection(0, initialName.length)
                requestTextInput()
            }

            // Set initial mode
            when (initialMode) {
                AccessControlMode.AcceptAll -> binding.modeAcceptAll.isChecked = true
                AccessControlMode.AcceptSelected -> binding.modeAcceptSelected.isChecked = true
                AccessControlMode.DenySelected -> binding.modeDenySelected.isChecked = true
            }
        }

        dialog.show()
    }
}
