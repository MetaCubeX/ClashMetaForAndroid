package com.github.kr328.clash.design

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.github.kr328.clash.design.databinding.DesignRuleSnippetBinding
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.RuleProviderItem
import com.google.android.material.switchmaterial.SwitchMaterial

class RuleSnippetDesign(context: Context) : Design<RuleSnippetDesign.Request>(context) {
    sealed class Request {
        object OpenCreateSheet : Request()
        object OpenManualRules : Request()
        data class ToggleProvider(val id: String, val name: String, val enabled: Boolean) : Request()
        data class DeleteProvider(val id: String, val name: String) : Request()
        data class EditProvider(val id: String, val name: String) : Request()
    }

    private val binding = DesignRuleSnippetBinding
        .inflate(context.layoutInflater, context.root, false)
    private var existingProviders: List<RuleProviderItem> = emptyList()

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.activityBarLayout.applyFrom(context)
        binding.btnOpenCreateSheet.setOnClickListener { requests.trySend(Request.OpenCreateSheet) }
        binding.btnOpenManualRules.setOnClickListener { requests.trySend(Request.OpenManualRules) }
    }

    fun patchExistingProviders(providers: List<RuleProviderItem>) {
        existingProviders = providers
        val container = binding.providerItemsContainer
        container.removeAllViews()
        if (existingProviders.isNotEmpty()) {
            existingProviders.forEach { provider ->
                container.addView(
                    buildRowSwitch(
                        title = provider.name,
                        subtitle = provider.url,
                        checked = provider.enabled,
                    ) { checked ->
                        requests.trySend(Request.ToggleProvider(provider.id, provider.name, checked))
                    }.also { row ->
                        val actions = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                        }
                        val edit = ImageButton(context).apply {
                            setImageResource(R.drawable.ic_baseline_edit)
                            background = null
                            layoutParams = LinearLayout.LayoutParams(80, 80).apply { marginEnd = 8 }
                            setOnClickListener { requests.trySend(Request.EditProvider(provider.id, provider.name)) }
                        }
                        val delete = ImageButton(context).apply {
                            setImageResource(R.drawable.ic_baseline_delete)
                            background = null
                            layoutParams = LinearLayout.LayoutParams(80, 80)
                            setOnClickListener { requests.trySend(Request.DeleteProvider(provider.id, provider.name)) }
                        }
                        actions.addView(edit)
                        actions.addView(delete)
                        (row as LinearLayout).addView(actions)
                    }
                )
            }
            return
        }
        container.addView(TextView(context).apply {
            text = context.getString(R.string.rule_existing_providers_empty)
            textSize = 12f
            alpha = 0.7f
        })
    }

    private fun buildRowSwitch(
        title: String,
        subtitle: String?,
        checked: Boolean,
        onChecked: (Boolean) -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
            setPadding(0, 8, 0, 8)
        }
        val sw = SwitchMaterial(context).apply {
            text = title
            isChecked = checked
            setOnCheckedChangeListener { _: CompoundButton, isCheckedNow: Boolean ->
                onChecked(isCheckedNow)
            }
        }
        row.addView(sw)
        if (!subtitle.isNullOrBlank()) {
            row.addView(TextView(context).apply {
                text = subtitle
                textSize = 12f
                alpha = 0.7f
                gravity = Gravity.START
            })
        }
        return row
    }
}
