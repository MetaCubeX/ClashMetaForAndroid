package com.github.kr328.clash

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.design.AppSettingsDesign
import com.github.kr328.clash.design.model.Behavior
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.theme.YumeTheme
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.ApplicationObserver
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class AppSettingsActivity : ComponentActivity(), Behavior {
    override var autoRestart: Boolean
        get() = readAutoRestart()
        set(value) {
            applyAutoRestart(value)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uiStore = UiStore(this)
        val srvStore = ServiceStore(this)
        val running = false

        val design = AppSettingsDesign(
            context = this,
            uiStore = uiStore,
            srvStore = srvStore,
            behavior = this,
            running = running,
            onHideIconChange = { hide -> onHideIconChange(hide) }
        )

        setContent {
            YumeTheme {
                design.Content()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                design.requests
                    .receiveAsFlow()
                    .collect { req: AppSettingsDesign.Request ->
                        if (req == AppSettingsDesign.Request.ReCreateAllActivities) {
                            ApplicationObserver.createdActivities.forEach { it.recreate() }
                        }
                    }
            }
        }
    }

    private fun readAutoRestart(): Boolean {
        val status = packageManager.getComponentEnabledSetting(
            RestartReceiver::class.componentName
        )
        return status == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    private fun applyAutoRestart(value: Boolean) {
        val status = if (value)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        packageManager.setComponentEnabledSetting(
            RestartReceiver::class.componentName,
            status,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun onHideIconChange(hide: Boolean) {
        val newState = if (hide) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        packageManager.setComponentEnabledSetting(
            ComponentName(this, mainActivityAlias),
            newState,
            PackageManager.DONT_KILL_APP
        )
    }
}