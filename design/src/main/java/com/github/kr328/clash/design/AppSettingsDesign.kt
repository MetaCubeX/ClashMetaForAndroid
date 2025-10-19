package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable
import com.github.kr328.clash.design.model.Behavior
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.store.ServiceStore

class AppSettingsDesign(
    context: Context,
    val uiStore: UiStore,
    val srvStore: ServiceStore,
    val behavior: Behavior,
    val running: Boolean,
    val onHideIconChange: (hide: Boolean) -> Unit,
) : Design<AppSettingsDesign.Request>(context) {
    enum class Request {
        ReCreateAllActivities
    }

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.AppSettingsScreen(this)
    }

    fun recreateAllActivities() {
        requests.trySend(Request.ReCreateAllActivities)
    }

    fun setDarkMode(index: Int) {
        val newMode = when (index) {
            0 -> com.github.kr328.clash.design.model.DarkMode.Auto
            1 -> com.github.kr328.clash.design.model.DarkMode.ForceLight
            else -> com.github.kr328.clash.design.model.DarkMode.ForceDark
        }
        if (uiStore.darkMode != newMode) {
            uiStore.darkMode = newMode
            recreateAllActivities()
        }
    }
}