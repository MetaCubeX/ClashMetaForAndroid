package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.github.kr328.clash.design.databinding.DesignSettingsExpandedBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class ExpandedSettingsDesign(context: Context) : Design<ExpandedSettingsDesign.Request>(context) {
    enum class Section(val menuId: Int, val titleRes: Int) {
        App(R.id.navigation_settings_app, R.string.app),
        Network(R.id.navigation_settings_network, R.string.network),
        Override(R.id.navigation_settings_override, R.string.override),
        MetaFeature(R.id.navigation_settings_meta_feature, R.string.meta_features),
    }

    sealed class Request {
        data class SelectSection(val section: Section) : Request()
        object ResetSection : Request()
    }

    private val binding = DesignSettingsExpandedBinding.inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    val slidingPaneLayout: SlidingPaneLayout
        get() = binding.slidingPaneLayout

    init {
        binding.self = this
        binding.settingsSectionsNavigation.setOnItemSelectedListener { item ->
            val section = Section.entries.firstOrNull { it.menuId == item.itemId } ?: return@setOnItemSelectedListener false
            requests.trySend(Request.SelectSection(section))
            true
        }
    }

    fun showSection(section: Section, showReset: Boolean) {
        binding.settingsSectionTitleView.setText(section.titleRes)
        binding.settingsSectionResetView.isVisible = showReset
        binding.settingsSectionsNavigation.menu.findItem(section.menuId)?.isChecked = true
    }

    fun showDetail(design: Design<*>) {
        val nextRoot = design.root
        val container = binding.settingsDetailContainer
        val currentRoot = container.getChildAt(0)

        if (currentRoot === nextRoot) {
            return
        }

        (nextRoot.parent as? ViewGroup)?.removeView(nextRoot)
        container.removeAllViews()
        container.addView(nextRoot)
    }

    fun requestReset() {
        requests.trySend(Request.ResetSection)
    }
}
