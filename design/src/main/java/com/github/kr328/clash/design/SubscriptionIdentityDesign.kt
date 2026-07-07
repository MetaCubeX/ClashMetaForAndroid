package com.github.kr328.clash.design

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.github.kr328.clash.design.databinding.DesignSubscriptionIdentityBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.google.android.material.R as MaterialR

class SubscriptionIdentityDesign(context: Context) : Design<SubscriptionIdentityDesign.Request>(context) {
    sealed interface Request {
        data object CopyHwid : Request
        data object CopySchemes : Request
        data object CopyHwidDiagnostics : Request
        data class OpenUrl(val url: String) : Request
    }

    private val binding = DesignSubscriptionIdentityBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private data class DocLink(val labelRes: Int, val iconRes: Int, val urlRes: Int)

    private val docLinks = listOf(
        DocLink(R.string.subscription_docs_overview, R.drawable.ic_outline_info, R.string.docs_url_operator_overview),
        DocLink(R.string.subscription_docs_headers, R.drawable.ic_outline_article, R.string.docs_url_operator_headers),
        DocLink(R.string.subscription_docs_quickstart, R.drawable.ic_baseline_bolt, R.string.docs_url_operator_quickstart),
        DocLink(R.string.subscription_docs_templates, R.drawable.ic_baseline_extension, R.string.docs_url_operator_templates),
        DocLink(R.string.subscription_docs_security, R.drawable.ic_baseline_vpn_lock, R.string.docs_url_operator_security),
        DocLink(R.string.subscription_docs_consolidated, R.drawable.ic_baseline_article, R.string.docs_url_supported_headers),
    )

    init {
        binding.self = this
        binding.header.screenTitle.text = context.getString(R.string.subscription_identity)
        renderDocsRows()
    }

    private fun renderDocsRows() {
        val container = binding.docsContainer
        container.removeAllViews()
        docLinks.forEachIndexed { i, link ->
            container.addView(buildDocRow(link))
            if (i < docLinks.lastIndex) container.addView(buildDivider())
        }
    }

    private fun buildDocRow(link: DocLink): View {
        val density = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).toInt()

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4), px(12), px(4), px(12))
            background = AppCompatResources.getDrawable(context, R.drawable.bg_proxy_node_row)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                requests.trySend(Request.OpenUrl(context.getString(link.urlRes)))
            }
        }
        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(px(22), px(22)).apply { marginEnd = px(14) }
            setImageResource(link.iconRes)
            imageTintList = ColorStateList.valueOf(
                context.resolveThemedColor(MaterialR.attr.colorPrimary)
            )
        }
        row.addView(icon)
        val label = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
            )
            text = context.getString(link.labelRes)
            setTextAppearance(R.style.TextAppearance_App_BodyMedium)
            setTextColor(context.resolveThemedColor(MaterialR.attr.colorOnSurface))
        }
        row.addView(label)
        val chevron = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(px(16), px(16))
            setImageResource(R.drawable.ic_baseline_expand_more)
            rotation = -90f
            imageTintList = ColorStateList.valueOf(
                context.resolveThemedColor(MaterialR.attr.colorOnSurfaceVariant)
            )
        }
        row.addView(chevron)
        return row
    }

    private fun buildDivider(): View {
        val density = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).toInt()
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(1),
            ).apply { setMargins(px(40), 0, 0, 0) }
            setBackgroundColor(
                context.resolveThemedColor(MaterialR.attr.colorOutlineVariant)
            )
        }
    }

    fun requestOpenOperatorApiSpec() {
        requests.trySend(Request.OpenUrl(context.getString(R.string.operator_api_spec_url)))
    }

    fun setHwid(value: String) {
        binding.textHwid.text = value
    }

    fun setSchemes(value: String) {
        binding.textSchemeList.text = value
    }

    fun requestCopyHwid() {
        requests.trySend(Request.CopyHwid)
    }

    fun requestCopySchemes() {
        requests.trySend(Request.CopySchemes)
    }

    fun setHwidDiagnostics(value: String) {
        binding.textHwidDiagnostics.text = value
    }

    fun requestCopyHwidDiagnostics() {
        requests.trySend(Request.CopyHwidDiagnostics)
    }
}
