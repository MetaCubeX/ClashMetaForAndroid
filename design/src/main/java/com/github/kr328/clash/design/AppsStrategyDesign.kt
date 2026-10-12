package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import com.github.kr328.clash.design.adapter.AppsStrategyConfigAdapter
import com.github.kr328.clash.design.databinding.DesignAppsStrategyBinding
import com.github.kr328.clash.design.databinding.DialogAppsStrategyConfigEditBinding
import com.github.kr328.clash.design.util.Validator
import com.github.kr328.clash.design.util.ValidatorAcceptAll
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.patchDataSet
import com.github.kr328.clash.design.util.requestTextInput
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppsStrategyConfig
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.resume

class AppsStrategyDesign(context: Context) : Design<AppsStrategyDesign.Request>(context) {

    sealed class Request {
        object Create : Request()
        data class Edit(val config: AppsStrategyConfig) : Request()
        data class Active(val config: AppsStrategyConfig) : Request()
        data class Choose(val config: AppsStrategyConfig) : Request()
    }

    private val binding = DesignAppsStrategyBinding
        .inflate(context.layoutInflater, context.root, false)

    private val adapter = AppsStrategyConfigAdapter(
        context,
        onClicked = { config ->
            requests.trySend(Request.Active(config))
        },
        onEditClicked = { config ->
            requests.trySend(Request.Edit(config))
        },
        onChooseClicked = { config ->
            requests.trySend(Request.Choose(config))
        }
    )

    override val root: View
        get() = binding.root

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.also {
            it.bindAppBarElevation(binding.activityBarLayout)
            it.applyLinearAdapter(context, adapter)
        }
    }

    suspend fun patchConfigs(configs: List<AppsStrategyConfig>, activeUuid: UUID?) {
        withContext(Dispatchers.Main) {
            adapter.apply {
                patchDataSet(this::configs, configs, id = { it.uuid })
                this.activeUuid = activeUuid
                notifyItemRangeChanged(0, configs.size)
            }
        }
    }

    fun requestCreate() {
        requests.trySend(Request.Create)
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
