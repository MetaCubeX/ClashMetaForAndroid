package com.github.kr328.clash.design

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.util.RuleEditFormHelper
import com.github.kr328.clash.design.util.RuleTypeCatalog
import com.github.kr328.clash.design.util.RuleTypeIcons
import com.github.kr328.clash.design.util.RuleTypeMeta
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleSource
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
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
    private val dialog = AppBottomSheetDialog(context, fitContentHeight = true)
    private val root: View = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_rule_edit, null)
    private val title: TextView = root.findViewById(R.id.sheet_title)
    private val typeLayout: TextInputLayout = root.findViewById(R.id.type_layout)
    private val typeInput: TextInputEditText = root.findViewById(R.id.input_type)
    private val valueLayout: TextInputLayout = root.findViewById(R.id.value_layout)
    private val valueInput: TextInputEditText = root.findViewById(R.id.input_value)
    private val policyBuiltinGroup: MaterialButtonToggleGroup = root.findViewById(R.id.policy_builtin_group)
    private val btnPolicyDirect: MaterialButton = root.findViewById(R.id.btn_policy_direct)
    private val btnPolicyReject: MaterialButton = root.findViewById(R.id.btn_policy_reject)
    private val groupLayout: TextInputLayout = root.findViewById(R.id.group_layout)
    private val groupInput: AutoCompleteTextView = root.findViewById(R.id.input_group)
    private val policyWarning: TextView = root.findViewById(R.id.policy_warning)
    private val livePreview: TextView = root.findViewById(R.id.live_preview)
    private val validationError: TextView = root.findViewById(R.id.validation_error)
    private val enabledSwitch: MaterialSwitch = root.findViewById(R.id.switch_enabled)
    private val btnDelete: MaterialButton = root.findViewById(R.id.btn_delete)
    private val btnCancel: MaterialButton = root.findViewById(R.id.btn_cancel)
    private val btnConfirm: MaterialButton = root.findViewById(R.id.btn_confirm)

    private var selectedMeta: RuleTypeMeta = RuleTypeCatalog.common.first()
    // Picker shows the real proxy/group names only. knownPolicies (UPPERCASED, for
    // validation) must NOT be mixed in here — it produced a duplicate of every node
    // in caps. Dedup case-insensitively, keeping the original-case name.
    private val groupPolicies = policyOptions
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filter { policy ->
            !policy.equals("DIRECT", ignoreCase = true) &&
                !policy.equals("REJECT", ignoreCase = true) &&
                !policy.equals("REJECT-DROP", ignoreCase = true)
        }
        .distinctBy { it.uppercase() }
        .sorted()

    init {
        dialog.setContentView(root)
        groupInput.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, groupPolicies),
        )
        typeInput.setOnClickListener { showTypePicker() }
        typeLayout.setEndIconOnClickListener { showTypePicker() }
        valueInput.addTextChangedListener { refreshFormState() }
        groupInput.addTextChangedListener { onGroupChanged() }
        groupInput.setOnItemClickListener { _, _, _, _ -> onGroupChanged() }
        groupInput.setOnFocusChangeListener { _, _ -> refreshPolicyWarning() }
        policyBuiltinGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_policy_direct, R.id.btn_policy_reject -> {
                        groupInput.setText("", false)
                    }
                }
            }
            stylePolicyButtons()
            refreshFormState()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener { submit() }
        btnDelete.setOnClickListener {
            onDelete?.invoke()
            dialog.dismiss()
        }
        stylePolicyButtons()
    }

    fun showAdd() {
        title.text = context.getString(R.string.rules_hub_add_rule)
        selectedMeta = RuleTypeCatalog.common.first()
        applySelectedMeta()
        valueInput.setText("")
        selectPolicy("DIRECT")
        enabledSwitch.isChecked = true
        btnDelete.visibility = View.GONE
        validationError.visibility = View.GONE
        valueLayout.error = null
        dialog.show()
        refreshFormState()
    }

    fun showEdit(rule: RuleItem) {
        title.text = context.getString(R.string.rules_hub_edit_rule)
        selectedMeta = RuleEditFormHelper.metaForType(rule.type)
        applySelectedMeta()
        valueInput.setText(rule.value)
        selectPolicy(rule.policy)
        enabledSwitch.isChecked = rule.enabled
        btnDelete.visibility = if (onDelete != null) View.VISIBLE else View.GONE
        validationError.visibility = View.GONE
        valueLayout.error = null
        dialog.show()
        refreshFormState()
    }

    private fun showTypePicker() {
        val pickerDialog = AppBottomSheetDialog(context, fitContentHeight = true)
        val pickerRoot = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_rule_type_picker, null)
        val list = pickerRoot.findViewById<LinearLayout>(R.id.type_picker_list)
        RuleTypeCatalog.common.forEach { meta ->
            list.addView(makeTypeRow(meta, showEngineType = false) {
                selectType(meta)
                pickerDialog.dismiss()
            })
        }
        list.addView(makeSectionHeader(R.string.rule_edit_type_more))
        RuleTypeCatalog.advanced.forEach { meta ->
            list.addView(makeTypeRow(meta, showEngineType = true) {
                selectType(meta)
                pickerDialog.dismiss()
            })
        }
        pickerDialog.setContentView(pickerRoot)
        pickerDialog.show()
    }

    private fun makeSectionHeader(titleRes: Int): TextView =
        TextView(context).apply {
            setText(titleRes)
            setPaddingRelative(paddingHorizontalPx(), paddingVerticalPx() * 2, paddingHorizontalPx(), paddingVerticalPx())
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        }

    private fun makeTypeRow(
        meta: RuleTypeMeta,
        showEngineType: Boolean,
        onPick: () -> Unit,
    ): View {
        val row = LayoutInflater.from(context).inflate(R.layout.item_rule_type_picker, null)
        row.findViewById<ImageView>(R.id.type_icon).setImageResource(RuleTypeIcons.forMihomoType(meta.mihomoType))
        row.findViewById<TextView>(R.id.type_title).text = RuleEditFormHelper.displayTitle(context, meta)
        val subtitle = row.findViewById<TextView>(R.id.type_subtitle)
        if (showEngineType) {
            subtitle.visibility = View.VISIBLE
            subtitle.text = meta.mihomoType
        } else {
            subtitle.visibility = View.GONE
        }
        row.setOnClickListener { onPick() }
        return row
    }

    private fun selectType(meta: RuleTypeMeta) {
        selectedMeta = meta
        applySelectedMeta()
        refreshFormState()
    }

    private fun applySelectedMeta() {
        val icon = RuleTypeIcons.forMihomoType(selectedMeta.mihomoType)
        typeInput.setText(RuleEditFormHelper.displayTitle(context, selectedMeta))
        typeInput.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        typeInput.compoundDrawablePadding = (12 * context.resources.displayMetrics.density).toInt()
        if (selectedMeta.requiresValue) {
            valueLayout.visibility = View.VISIBLE
            valueLayout.hint = RuleEditFormHelper.displayHint(context, selectedMeta)
            valueInput.inputType = when (selectedMeta.keyboard) {
                RuleTypeMeta.Keyboard.NUMBER ->
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                RuleTypeMeta.Keyboard.TEXT ->
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
        } else {
            valueLayout.visibility = View.GONE
            valueInput.setText("")
            valueLayout.error = null
        }
    }

    private fun onGroupChanged() {
        val group = groupInput.text?.toString()?.trim().orEmpty()
        if (group.isNotBlank()) {
            policyBuiltinGroup.clearChecked()
        }
        stylePolicyButtons()
        refreshPolicyWarning()
        refreshFormState()
    }

    private fun selectPolicy(policy: String) {
        val normalized = policy.trim()
        when {
            normalized.equals("DIRECT", ignoreCase = true) -> {
                policyBuiltinGroup.check(R.id.btn_policy_direct)
                groupInput.setText("", false)
            }
            normalized.equals("REJECT", ignoreCase = true) ||
                normalized.equals("REJECT-DROP", ignoreCase = true) -> {
                policyBuiltinGroup.check(R.id.btn_policy_reject)
                groupInput.setText("", false)
            }
            normalized.isNotBlank() -> {
                policyBuiltinGroup.clearChecked()
                groupInput.setText(normalized, false)
            }
            else -> {
                policyBuiltinGroup.clearChecked()
                groupInput.setText("", false)
            }
        }
        stylePolicyButtons()
        refreshPolicyWarning()
    }

    private fun resolvedPolicy(): String {
        when (policyBuiltinGroup.checkedButtonId) {
            R.id.btn_policy_direct -> return "DIRECT"
            R.id.btn_policy_reject -> return "REJECT"
        }
        return groupInput.text?.toString()?.trim().orEmpty()
    }

    private fun refreshFormState() {
        val value = valueInput.text?.toString().orEmpty()
        val policy = resolvedPolicy()
        val valueError = if (selectedMeta.requiresValue) {
            selectedMeta.validate(value)
        } else {
            null
        }

        if (valueError != null) {
            val message = RuleEditFormHelper.valueErrorMessage(context, valueError)
            valueLayout.error = message
            validationError.text = message
            validationError.visibility = View.VISIBLE
        } else {
            valueLayout.error = null
            validationError.visibility = View.GONE
        }

        livePreview.text = if (policy.isBlank()) {
            context.getString(R.string.rule_edit_live_preview_empty)
        } else {
            RuleEditFormHelper.previewLine(selectedMeta.mihomoType, value, policy)
        }

        refreshPolicyWarning()
        btnConfirm.isEnabled = valueError == null && policy.isNotBlank()
    }

    private fun refreshPolicyWarning() {
        val policy = resolvedPolicy()
        val missing = policy.isNotBlank() &&
            knownPolicies.none { it.equals(policy, ignoreCase = true) }
        policyWarning.visibility = if (missing) View.VISIBLE else View.GONE
    }

    private fun stylePolicyButtons() {
        val directChecked = policyBuiltinGroup.checkedButtonId == R.id.btn_policy_direct
        val rejectChecked = policyBuiltinGroup.checkedButtonId == R.id.btn_policy_reject
        tintPolicyButton(
            btnPolicyDirect,
            directChecked,
            context.getColor(R.color.policy_direct_container),
            context.getColor(R.color.policy_on_direct_container),
        )
        tintPolicyButton(
            btnPolicyReject,
            rejectChecked,
            MaterialColors.getColor(btnPolicyReject, com.google.android.material.R.attr.colorErrorContainer),
            MaterialColors.getColor(btnPolicyReject, com.google.android.material.R.attr.colorOnErrorContainer),
        )
    }

    private fun tintPolicyButton(
        button: MaterialButton,
        checked: Boolean,
        checkedBg: Int,
        checkedText: Int,
    ) {
        // Tint the Material shape drawable — never setBackgroundColor()/background=null,
        // which overwrite it and crash MaterialButtonToggleGroup.updateChildShapes
        // ("MaterialButton which has an overwritten background").
        if (checked) {
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(checkedBg)
            button.setTextColor(checkedText)
            button.strokeWidth = 0
        } else {
            button.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            button.setTextColor(MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurface))
            button.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
            button.strokeColor = android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(button, com.google.android.material.R.attr.colorOutline),
            )
        }
    }

    private fun submit() {
        if (!btnConfirm.isEnabled) return
        val value = if (selectedMeta.requiresValue) {
            valueInput.text?.toString()?.trim().orEmpty()
        } else {
            ""
        }
        onConfirm(
            RuleEditResult(
                type = selectedMeta.mihomoType,
                value = value,
                policy = resolvedPolicy(),
                enabled = enabledSwitch.isChecked,
            ),
        )
        dialog.dismiss()
    }

    private fun paddingHorizontalPx(): Int = (16 * context.resources.displayMetrics.density).toInt()

    private fun paddingVerticalPx(): Int = (8 * context.resources.displayMetrics.density).toInt()

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
