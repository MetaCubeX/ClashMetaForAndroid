package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.databinding.DesignSettingsBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class SettingsDesign(context: Context) : Design<SettingsDesign.Request>(context) {
    enum class Request {
        StartApp,
        StartTheme,
        StartNetwork,
        StartAppRouting,
        StartFeatures,
        StartAdvanced,
        StartAnnouncement,
        StartLogs,
        StartAbout,
    }

    private val binding = DesignSettingsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.toolbar.title = context.getString(R.string.settings)
        binding.toolbar.setNavigationOnClickListener {
            (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
