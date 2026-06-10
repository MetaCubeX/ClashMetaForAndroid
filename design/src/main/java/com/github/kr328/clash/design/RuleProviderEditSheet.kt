package com.github.kr328.clash.design

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import com.github.kr328.clash.service.model.RuleProviderItem
import com.github.kr328.clash.service.model.RuleSource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID

data class RuleProviderEditResult(
    val name: String,
    val url: String,
    val behavior: String,
    val intervalHours: Int,
    val enabled: Boolean,
)

class RuleProviderEditSheet(
    private val context: Context,
    private val onConfirm: (RuleProviderEditResult) -> Unit,
    private val onDelete: (() -> Unit)? = null,
) {
    private val dialog = BottomSheetDialog(context)
    private val root: View = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_rule_provider_edit, null)
    private val title: TextView = root.findViewById(R.id.sheet_title)
    private val nameInput: TextInputEditText = root.findViewById(R.id.input_name)
    private val urlInput: TextInputEditText = root.findViewById(R.id.input_url)
    private val behaviorInput: AutoCompleteTextView = root.findViewById(R.id.input_behavior)
    private val intervalInput: TextInputEditText = root.findViewById(R.id.input_interval)
    private val enabledSwitch: MaterialSwitch = root.findViewById(R.id.switch_enabled)
    private val validationError: TextView = root.findViewById(R.id.validation_error)
    private val btnDelete: MaterialButton = root.findViewById(R.id.btn_delete)
    private val btnCancel: MaterialButton = root.findViewById(R.id.btn_cancel)
    private val btnConfirm: MaterialButton = root.findViewById(R.id.btn_confirm)

    private val behaviors = context.resources.getStringArray(R.array.rule_provider_behaviors).toList()

    init {
        dialog.setContentView(root)
        behaviorInput.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, behaviors),
        )
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener { submit() }
        btnDelete.setOnClickListener {
            onDelete?.invoke()
            dialog.dismiss()
        }
    }

    fun showAdd() {
        title.text = context.getString(R.string.rules_hub_add_provider)
        nameInput.setText("")
        urlInput.setText("")
        behaviorInput.setText(behaviors.first(), false)
        intervalInput.setText("24")
        enabledSwitch.isChecked = true
        btnDelete.visibility = View.GONE
        validationError.visibility = View.GONE
        dialog.show()
    }

    fun showEdit(provider: RuleProviderItem) {
        title.text = context.getString(R.string.rules_hub_edit_provider)
        nameInput.setText(provider.name)
        urlInput.setText(provider.url)
        behaviorInput.setText(provider.behavior.ifBlank { behaviors.first() }, false)
        val hours = (provider.interval / 3600).coerceAtLeast(1)
        intervalInput.setText(hours.toString())
        enabledSwitch.isChecked = provider.enabled
        btnDelete.visibility = if (onDelete != null) View.VISIBLE else View.GONE
        validationError.visibility = View.GONE
        dialog.show()
    }

    private fun submit() {
        val name = nameInput.text?.toString()?.trim().orEmpty()
        val url = urlInput.text?.toString()?.trim().orEmpty()
        val behavior = behaviorInput.text?.toString()?.trim().orEmpty().ifBlank { behaviors.first() }
        val hours = intervalInput.text?.toString()?.trim()?.toIntOrNull()
        val enabled = enabledSwitch.isChecked

        val error = when {
            name.isBlank() -> context.getString(R.string.rules_hub_provider_err_name)
            url.isBlank() -> context.getString(R.string.rules_hub_provider_err_url)
            hours == null || hours <= 0 -> context.getString(R.string.rules_hub_provider_err_interval)
            else -> null
        }
        if (error != null) {
            validationError.text = error
            validationError.visibility = View.VISIBLE
            return
        }
        validationError.visibility = View.GONE
        onConfirm(
            RuleProviderEditResult(
                name = name,
                url = url,
                behavior = behavior,
                intervalHours = hours!!,
                enabled = enabled,
            ),
        )
        dialog.dismiss()
    }

    companion object {
        fun toProviderItem(
            result: RuleProviderEditResult,
            existing: RuleProviderItem? = null,
        ): RuleProviderItem {
            val path = existing?.path?.takeIf { it.isNotBlank() }
                ?: "./ruleset/${result.name}.yaml"
            return RuleProviderItem(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = result.name,
                type = existing?.type ?: "http",
                behavior = result.behavior,
                url = result.url,
                interval = result.intervalHours * 3600,
                path = path,
                format = existing?.format.orEmpty(),
                enabled = result.enabled,
                source = existing?.source ?: RuleSource.MANUAL,
            )
        }
    }
}
