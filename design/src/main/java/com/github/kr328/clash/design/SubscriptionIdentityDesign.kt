package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.databinding.DesignSubscriptionIdentityBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class SubscriptionIdentityDesign(context: Context) : Design<SubscriptionIdentityDesign.Request>(context) {
    enum class Request {
        CopyHwid,
        CopySchemes,
        CopyHwidDiagnostics,
        OpenOperatorApiSpec,
    }

    private val binding = DesignSubscriptionIdentityBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.toolbar.title = context.getString(R.string.subscription_identity)
        binding.toolbar.setNavigationOnClickListener {
            (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
        }
        binding.textSupportedResponseHeaders.text = buildSupportedResponseHeadersText(context)
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

    fun requestOpenOperatorApiSpec() {
        requests.trySend(Request.OpenOperatorApiSpec)
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
