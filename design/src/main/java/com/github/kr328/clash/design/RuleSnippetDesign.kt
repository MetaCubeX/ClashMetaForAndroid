package com.github.kr328.clash.design

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.github.kr328.clash.design.databinding.DesignRuleSnippetBinding
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.RuleProviderItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt

class RuleSnippetDesign(context: Context) : Design<RuleSnippetDesign.Request>(context) {
    sealed class Request {
        object UpdateAllRuleSets : Request()
        data class UpdateProvider(val name: String) : Request()
    }

    private val binding = DesignRuleSnippetBinding
        .inflate(context.layoutInflater, context.root, false)
    private var existingProviders: List<RuleProviderItem> = emptyList()
    private var providerEntryCounts: Map<String, Int> = emptyMap()

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.header.screenTitle.text = context.getString(R.string.rule_snippet_title)
        binding.btnUpdateAllRuleSets.setOnClickListener { requests.trySend(Request.UpdateAllRuleSets) }
    }

    fun patchExistingProviders(
        providers: List<RuleProviderItem>,
        entryCounts: Map<String, Int> = emptyMap(),
    ) {
        existingProviders = providers
        providerEntryCounts = entryCounts
        val container = binding.providerItemsContainer
        container.removeAllViews()
        if (existingProviders.isNotEmpty()) {
            val columns = when {
                context.resources.configuration.screenWidthDp >= 840 -> 4
                context.resources.configuration.screenWidthDp >= 600 -> 2
                else -> 1
            }
            existingProviders.chunked(columns).forEach { rowProviders ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBaselineAligned(false)
                }
                rowProviders.forEach { provider ->
                    row.addView(
                        buildExistingProviderCard(provider).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f,
                            ).apply {
                                setMargins(0, 10.dp(), 10.dp(), 0)
                            }
                        },
                    )
                }
                repeat(columns - rowProviders.size) {
                    row.addView(
                        View(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                1,
                                1f,
                            ).apply {
                                setMargins(0, 10.dp(), 10.dp(), 0)
                            }
                        },
                    )
                }
                container.addView(row)
            }
            return
        }
        container.addView(TextView(context).apply {
            text = context.getString(R.string.rule_existing_providers_empty)
            textSize = 12f
            alpha = 0.7f
        })
    }

    private fun buildExistingProviderCard(provider: RuleProviderItem): View {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 12.dp(), 12.dp(), 12.dp())
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(context).apply {
                text = provider.name
                setTextAppearance(R.style.TextAppearance_App_TitleSmall)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                )
            },
        )
        header.addView(
            iconButton(
                icon = R.drawable.ic_baseline_sync,
                description = context.getString(R.string.update),
                onClick = { requests.trySend(Request.UpdateProvider(provider.name)) },
            ),
        )
        content.addView(header)
        if (provider.url.isNotBlank()) {
            content.addView(
                TextView(context).apply {
                    text = provider.url
                    setPadding(0, 8.dp(), 0, 0)
                    setTextAppearance(R.style.TextAppearance_App_BodySmall)
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                    setTextIsSelectable(true)
                },
            )
        }
        content.addView(
            TextView(context).apply {
                val behaviorLabel = provider.behavior.replaceFirstChar { it.uppercaseChar() }
                val typeLabel = provider.type.uppercase()
                val parts = mutableListOf(behaviorLabel, typeLabel)
                providerEntryCounts[provider.name]?.let { count ->
                    parts += context.getString(R.string.rule_provider_entries_fmt, count.toString())
                }
                text = parts.joinToString(" · ")
                setPadding(0, 8.dp(), 0, 0)
                setTextAppearance(R.style.TextAppearance_App_BodySmall)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
        )
        return MaterialCardView(context).apply {
            radius = 18f
            cardElevation = 0f
            setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer))
            strokeWidth = 1
            strokeColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
            addView(content)
        }
    }

    private fun iconButton(
        icon: Int,
        description: String,
        onClick: () -> Unit,
    ): MaterialButton {
        return MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            text = ""
            setIconResource(icon)
            contentDescription = description
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            insetTop = 0
            insetBottom = 0
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setPadding(10.dp(), 0, 10.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(40.dp(), 36.dp()).apply {
                marginStart = 6.dp()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun Int.dp(): Int =
        (this * context.resources.displayMetrics.density).roundToInt()
}
