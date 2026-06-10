package com.github.kr328.clash.design

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleSource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

data class RuleEditResult(
    val type: String,
    val value: String,
    val policy: String,
    val enabled: Boolean,
    val deleted: Boolean = false,
)

class RuleEditSheet(
    private val context: Context,
    private val policyOptions: List<String>,
    private val knownPolicies: Set<String>,
    private val onConfirm: (RuleEditResult) -> Unit,
    private val onDelete: (() -> Unit)? = null,
) {
    private val dialog = BottomSheetDialog(context)
    private val root: View = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_rule_edit, null)
    private val title: TextView = root.findViewById(R.id.sheet_title)
    private val typeInput: AutoCompleteTextView = root.findViewById(R.id.input_type)
    private val valueLayout: TextInputLayout = root.findViewById(R.id.value_layout)
    private val valueInput: TextInputEditText = root.findViewById(R.id.input_value)
    private val policyInput: AutoCompleteTextView = root.findViewById(R.id.input_policy)
    private val policyWarning: TextView = root.findViewById(R.id.policy_warning)
    private val validationError: TextView = root.findViewById(R.id.validation_error)
    private val enabledSwitch: MaterialSwitch = root.findViewById(R.id.switch_enabled)
    private val btnDelete: MaterialButton = root.findViewById(R.id.btn_delete)
    private val btnCancel: MaterialButton = root.findViewById(R.id.btn_cancel)
    private val btnConfirm: MaterialButton = root.findViewById(R.id.btn_confirm)

    private val ruleTypes = context.resources.getStringArray(R.array.clash_manual_rule_types).toList()
    private val allPolicies = (RulesHubRowBuilder.builtInPolicies.toList() + policyOptions).distinct()

    init {
        dialog.setContentView(root)
        typeInput.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, ruleTypes),
        )
        policyInput.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, allPolicies),
        )
        typeInput.setOnItemClickListener { _, _, _, _ -> refreshValueVisibility() }
        policyInput.setOnFocusChangeListener { _, _ -> refreshPolicyWarning() }
        policyInput.setOnItemClickListener { _, _, _, _ -> refreshPolicyWarning() }
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener { submit() }
        btnDelete.setOnClickListener {
            onDelete?.invoke()
            dialog.dismiss()
        }
    }

    fun showAdd() {
        title.text = context.getString(R.string.rules_hub_add_rule)
        typeInput.setText(ruleTypes.firstOrNull().orEmpty(), false)
        valueInput.setText("")
        policyInput.setText(allPolicies.firstOrNull().orEmpty(), false)
        enabledSwitch.isChecked = true
        btnDelete.visibility = View.GONE
        validationError.visibility = View.GONE
        refreshValueVisibility()
        refreshPolicyWarning()
        dialog.show()
    }

    fun showEdit(rule: RuleItem) {
        title.text = context.getString(R.string.rules_hub_edit_rule)
        typeInput.setText(rule.type, false)
        valueInput.setText(rule.value)
        policyInput.setText(rule.policy, false)
        enabledSwitch.isChecked = rule.enabled
        btnDelete.visibility = if (onDelete != null) View.VISIBLE else View.GONE
        validationError.visibility = View.GONE
        refreshValueVisibility()
        refreshPolicyWarning()
        dialog.show()
    }

    private fun refreshValueVisibility() {
        val isMatch = typeInput.text?.toString().orEmpty().equals("MATCH", true)
        valueLayout.visibility = if (isMatch) View.GONE else View.VISIBLE
    }

    private fun refreshPolicyWarning() {
        val policy = policyInput.text?.toString()?.trim().orEmpty()
        val missing = policy.isNotBlank() &&
            knownPolicies.none { it.equals(policy, ignoreCase = true) }
        policyWarning.visibility = if (missing) View.VISIBLE else View.GONE
    }

    private fun submit() {
        val type = typeInput.text?.toString()?.trim().orEmpty()
        val value = valueInput.text?.toString()?.trim().orEmpty()
        val policy = policyInput.text?.toString()?.trim().orEmpty()
        val enabled = enabledSwitch.isChecked

        val error = when {
            type.isBlank() -> context.getString(R.string.rules_hub_err_type)
            !type.equals("MATCH", true) && value.isBlank() ->
                context.getString(R.string.rules_hub_err_value)
            policy.isBlank() -> context.getString(R.string.rules_hub_err_policy)
            else -> null
        }
        if (error != null) {
            validationError.text = error
            validationError.visibility = View.VISIBLE
            return
        }
        validationError.visibility = View.GONE
        onConfirm(
            RuleEditResult(
                type = type,
                value = value,
                policy = policy,
                enabled = enabled,
            ),
        )
        dialog.dismiss()
    }

    companion object {
        fun newManualRule(
            result: RuleEditResult,
            order: Int,
            id: String,
        ): RuleItem = RuleItem(
            id = id,
            type = result.type,
            value = result.value,
            policy = result.policy,
            enabled = result.enabled,
            deleted = result.deleted,
            source = RuleSource.MANUAL,
            order = order,
        )
    }
}
