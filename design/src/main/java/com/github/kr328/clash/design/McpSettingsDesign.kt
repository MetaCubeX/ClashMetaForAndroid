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
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.github.kr328.clash.service.store.ServiceStore
import universe.agentic.clash.AgenticClashTheme
import universe.agentic.clash.McpSettingsScreen
import universe.agentic.clash.McpSettingsUi
import universe.agentic.device.clash.McpProtocol

class McpSettingsDesign(context: Context) : Design<McpSettingsDesign.Request>(context) {
    sealed interface Request {
        data class Enabled(val on: Boolean) : Request
        data object CopyEndpoint : Request
        data object CopyToken : Request
    }

    private val store = ServiceStore(context)
    private var insets by mutableStateOf(Insets.EMPTY)
    private var state by mutableStateOf(read())

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val title = (context as? Activity)?.title?.toString()?.ifBlank { null }
            ?: context.getString(R.string.mcp_service)
        setContent {
            AgenticClashTheme {
                McpSettingsScreen(
                    insets = insets,
                    title = title,
                    state = state,
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onEnabled = { requests.trySend(Request.Enabled(it)) },
                    onCopyEndpoint = { requests.trySend(Request.CopyEndpoint) },
                    onCopyToken = { requests.trySend(Request.CopyToken) },
                )
            }
        }
    }

    fun refresh() {
        state = read()
    }

    fun endpoint(): String = state.endpoint

    fun token(): String = state.token

    private fun read(): McpSettingsUi {
        val port = store.mcpPort.takeIf { it in 1..65535 } ?: McpProtocol.DEFAULT_PORT
        return McpSettingsUi(
            enabled = store.mcpEnabled,
            endpoint = "http://127.0.0.1:$port${McpProtocol.PATH}",
            token = store.mcpToken,
            status = store.mcpStatus,
        )
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
