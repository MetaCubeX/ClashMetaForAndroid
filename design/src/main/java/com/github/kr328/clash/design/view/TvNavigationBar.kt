package com.github.kr328.clash.design.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.github.kr328.clash.design.databinding.ComponentTvNavigationBarBinding
import com.github.kr328.clash.design.util.layoutInflater

class TvNavigationBar @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
) : LinearLayout(context, attributeSet) {
    enum class Tab {
        Home,
        Proxy,
        Profiles,
        Logs,
        Settings,
        Help,
        About,
    }

    private val binding = ComponentTvNavigationBarBinding
        .inflate(context.layoutInflater, this, true)

    private val items by lazy {
        mapOf(
            Tab.Home to binding.tvNavHome,
            Tab.Proxy to binding.tvNavProxy,
            Tab.Profiles to binding.tvNavProfiles,
            Tab.Logs to binding.tvNavLogs,
            Tab.Settings to binding.tvNavSettings,
            Tab.Help to binding.tvNavHelp,
            Tab.About to binding.tvNavAbout,
        )
    }

    var onTabSelected: ((Tab) -> Unit)? = null

    var proxyEnabled: Boolean
        get() = binding.tvNavProxy.isEnabled
        set(value) {
            binding.tvNavProxy.isEnabled = value
            binding.tvNavProxy.alpha = if (value) 1f else 0.45f
        }

    init {
        orientation = HORIZONTAL
        clipChildren = false
        items.forEach { (tab, view) ->
            view.setOnClickListener { onTabSelected?.invoke(tab) }
        }
    }

    fun setActiveTab(tab: Tab) {
        items.forEach { (itemTab, view) -> view.isSelected = itemTab == tab }
    }

    fun setNextFocusDown(tab: Tab, viewId: Int) {
        items[tab]?.nextFocusDownId = viewId
    }

    fun focus(tab: Tab) {
        items[tab]?.requestFocus()
    }
}
