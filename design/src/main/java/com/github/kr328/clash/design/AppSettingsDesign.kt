package com.github.kr328.clash.design

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.kr328.clash.design.ios.AppSettingsScreen
import com.github.kr328.clash.design.ios.AppSettingsUiState
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.common.AppLanguages
import com.github.kr328.clash.design.model.Behavior
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSettingsDesign(
    context: Context,
    private val uiStore: UiStore,
    private val srvStore: ServiceStore,
    private val behavior: Behavior,
    running: Boolean,
    private val onHideIconChange: (hide: Boolean) -> Unit,
) : Design<AppSettingsDesign.Request>(context) {
    enum class Request {
        ReCreateAllActivities
    }

    private var insets by mutableStateOf(Insets.EMPTY)
    private var state by mutableStateOf(
        AppSettingsUiState(
            autoRestart = behavior.autoRestart,
            appLanguage = uiStore.appLanguage,
            darkMode = uiStore.darkMode,
            hideAppIcon = uiStore.hideAppIcon,
            hideFromRecents = uiStore.hideFromRecents,
            dynamicNotification = srvStore.dynamicNotification,
            running = running,
        )
    )

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val title = (context as? Activity)?.title?.toString()
            ?.ifBlank { null }
            ?: context.getString(R.string.app)
        setContent {
            ClashMetaXTheme {
                AppSettingsScreen(
                    insets = insets,
                    title = title,
                    state = state,
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onAutoRestart = ::setAutoRestart,
                    onLanguage = ::setLanguage,
                    onDarkMode = ::setDarkMode,
                    onHideIcon = ::setHideIcon,
                    onHideFromRecents = ::setHideFromRecents,
                    onShowTraffic = ::setDynamicNotification,
                )
            }
        }
    }

    private fun setAutoRestart(value: Boolean) {
        launch(Dispatchers.Main) {
            val stored = withContext(Dispatchers.IO) {
                behavior.autoRestart = value
                behavior.autoRestart
            }
            state = state.copy(autoRestart = stored)
        }
    }

    private fun setLanguage(value: String) {
        if (value == state.appLanguage) return
        launch(Dispatchers.Main) {
            val stored = withContext(Dispatchers.IO) {
                AppLanguages.write(context, value)
            }
            AppLanguages.apply(context, stored)
            AppLanguages.broadcast(context, stored)
            state = state.copy(appLanguage = stored)
            requests.trySend(Request.ReCreateAllActivities)
        }
    }

    private fun setDarkMode(value: DarkMode) {
        launch(Dispatchers.Main) {
            val stored = withContext(Dispatchers.IO) {
                uiStore.darkMode = value
                uiStore.darkMode
            }
            state = state.copy(darkMode = stored)
            requests.trySend(Request.ReCreateAllActivities)
        }
    }

    private fun setHideIcon(value: Boolean) {
        launch(Dispatchers.Main) {
            val stored = withContext(Dispatchers.IO) {
                uiStore.hideAppIcon = value
                uiStore.hideAppIcon
            }
            state = state.copy(hideAppIcon = stored)
            onHideIconChange(stored)
        }
    }

    private fun setHideFromRecents(value: Boolean) {
        launch(Dispatchers.Main) {
            val stored = withContext(Dispatchers.IO) {
                uiStore.hideFromRecents = value
                uiStore.hideFromRecents
            }
            state = state.copy(hideFromRecents = stored)
            requests.trySend(Request.ReCreateAllActivities)
        }
    }

    private fun setDynamicNotification(value: Boolean) {
        if (!state.showTrafficEnabled) return
        launch(Dispatchers.Main) {
            val stored = withContext(Dispatchers.IO) {
                srvStore.dynamicNotification = value
                srvStore.dynamicNotification
            }
            state = state.copy(dynamicNotification = stored)
        }
    }

    init {
        if (context is AppCompatActivity) {
            context.window.decorView.setOnInsertsChangedListener {
                if (surface.insets != it) surface.insets = it
                insets = it
            }
        }
    }
}
