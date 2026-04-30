package com.github.kr328.clash.design

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.github.kr328.clash.design.databinding.DesignRuleSnippetBinding
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.RuleProviderItem
import com.google.android.material.card.MaterialCardView
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
        binding.toolbar.title = ""
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
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
        }
        content.addView(
            TextView(context).apply {
                text = title
                setTextAppearance(R.style.TextAppearance_App_TitleSmall)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            },
        )
        if (!subtitle.isNullOrBlank()) {
            content.addView(
                TextView(context).apply {
                    text = subtitle
                    setPadding(0, 4, 0, 0)
                    setTextAppearance(R.style.TextAppearance_App_BodySmall)
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
        }
        return MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 10 }
            radius = 18f
            cardElevation = 0f
            setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer))
            strokeWidth = 1
            strokeColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
            addView(content)
        }
    }
}
