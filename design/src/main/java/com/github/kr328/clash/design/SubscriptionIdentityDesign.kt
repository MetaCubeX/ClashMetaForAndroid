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
        binding.toolbar.title = context.getString(R.string.subscription_identity)
        binding.toolbar.setNavigationOnClickListener {
            (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
        }
        binding.textSupportedResponseHeaders.text = buildSupportedResponseHeadersText(context)
        renderDocsRows()
    }

    private fun buildSupportedResponseHeadersText(context: Context): String {
        // Single source of truth for the in-app reference — same set of headers
        // documented in docs/operator-api/. Operator-facing spec lives there;
        // this is the user-facing "what is supported".
        val rows = listOf(
            "Subscription-Userinfo" to context.getString(R.string.headers_desc_userinfo),
            "profile-title" to context.getString(R.string.headers_desc_profile_title),
            "profile-update-interval" to context.getString(R.string.headers_desc_update_interval),
            "share-links" to context.getString(R.string.headers_desc_share_links),
            "x-hwid-*" to context.getString(R.string.headers_desc_x_hwid),
            "announce / announce-url" to context.getString(R.string.headers_desc_announce),
            "support-url (legacy)" to context.getString(R.string.headers_desc_legacy_support),
            "X-Branding-Enabled" to context.getString(R.string.headers_desc_branding_enabled),
            "X-Brand-Name" to context.getString(R.string.headers_desc_brand_name),
            "X-Brand-Tagline" to context.getString(R.string.headers_desc_brand_tagline),
            "X-Brand-Logo-URL" to context.getString(R.string.headers_desc_brand_logo),
            "X-Brand-Logo-Light-URL" to context.getString(R.string.headers_desc_brand_logo_light),
            "X-Brand-Accent-Color" to context.getString(R.string.headers_desc_brand_accent),
            "X-Brand-Website-URL" to context.getString(R.string.headers_desc_brand_website),
            "X-Brand-Support-URL" to context.getString(R.string.headers_desc_brand_support),
            "X-Brand-Telegram-URL" to context.getString(R.string.headers_desc_brand_telegram),
            "X-Brand-Bot-URL" to context.getString(R.string.headers_desc_brand_bot),
            "X-Brand-Privacy-URL" to context.getString(R.string.headers_desc_brand_privacy),
            "X-Brand-Terms-URL" to context.getString(R.string.headers_desc_brand_terms),
            "X-Brand-Help-URL" to context.getString(R.string.headers_desc_brand_help),
            "X-Brand-Status-URL" to context.getString(R.string.headers_desc_brand_status),
            "X-Brand-Renew-URL" to context.getString(R.string.headers_desc_brand_renew),
            "X-Brand-User-Display-Name" to context.getString(R.string.headers_desc_brand_user_display_name),
            "X-Brand-Greeting" to context.getString(R.string.headers_desc_brand_greeting),
            "X-Brand-Show-Operator-Tab" to context.getString(R.string.headers_desc_brand_show_tab),
            "X-Brand-Hide-Routing" to context.getString(R.string.headers_desc_brand_hide_routing),
        )
        return rows.joinToString("\n\n") { (key, desc) -> "$key\n  $desc" }
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
