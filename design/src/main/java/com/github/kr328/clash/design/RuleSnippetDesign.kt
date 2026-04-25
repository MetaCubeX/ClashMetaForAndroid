package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.github.kr328.clash.design.databinding.DesignRuleSnippetBinding
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.RuleProviderItem
import com.google.android.material.color.MaterialColors

class RuleSnippetDesign(context: Context) : Design<RuleSnippetDesign.Request>(context) {
    sealed class Request {
        object OpenCreateSheet : Request()
        object OpenManualRules : Request()
    }

    private val binding = DesignRuleSnippetBinding
        .inflate(context.layoutInflater, context.root, false)
    private var existingProviders: List<RuleProviderItem> = emptyList()

    override val root: View
        get() = binding.root

    init {
        binding.self = this
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
                    buildExistingProviderRow(
                        title = provider.name,
                        subtitle = provider.url,
                    ),
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

    /** Read-only: name + URL (no edit/delete; manage RULE-SET in Routing). */
    private fun buildExistingProviderRow(
        title: String,
        subtitle: String?,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 10 }
            setPadding(0, 8, 0, 8)
        }
        row.addView(
            TextView(context).apply {
                text = title
                setTextAppearance(R.style.TextAppearance_App_TitleSmall)
                try {
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
                } catch (_: IllegalArgumentException) {
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                }
            },
        )
        if (!subtitle.isNullOrBlank()) {
            row.addView(
                TextView(context).apply {
                    text = subtitle
                    setTextAppearance(R.style.TextAppearance_App_BodySmall)
                    try {
                        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                    } catch (_: IllegalArgumentException) {
                        alpha = 0.75f
                    }
                    maxLines = 4
                },
            )
        }
        return row
    }
}
