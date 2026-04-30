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
