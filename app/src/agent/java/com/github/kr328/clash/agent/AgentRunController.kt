package com.github.kr328.clash.agent

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.github.kr328.clash.agent.model.AgentConversationMessage
import com.github.kr328.clash.agent.model.AgentProviderSettings
import com.github.kr328.clash.agent.model.AgentRunEvent
import com.github.kr328.clash.agent.model.AgentTraceEntry
import com.github.kr328.clash.agent.runtime.AgentApprovalHandler
import com.github.kr328.clash.agent.runtime.AgentEngine
import com.github.kr328.clash.agent.runtime.AgentEngine.AgentScenario
import com.github.kr328.clash.agent.settings.AgentConversationStore
import com.github.kr328.clash.agent.tools.AgentToolSpec
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application-scoped runner for agent generations. A run keeps executing even
 * when the chat Activity is destroyed (user navigates back); the Activity only
 * observes [state] and renders it. Explicit [cancel] (stop button) is the only
 * way to abort a run, other than process death.
 */
object AgentRunController {
    data class RunState(
        val running: Boolean = false,
        val prompt: String = "",
        val scenario: AgentScenario = AgentScenario.GENERAL,
        val trace: List<AgentTraceEntry> = emptyList(),
        val streamed: String = "",
        val status: String = "",
        val error: String? = null,
        val messageId: String? = null,
    )

    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    // Provided by the chat Activity while it is alive. Used to resolve the
    // Android VPN consent dialog. When null (Activity gone), VPN starts that
    // need consent fail gracefully instead of crashing.
    @Volatile
    private var vpnConsentRequest: (suspend (Intent) -> Int)? = null

    // UI callbacks; null when the chat Activity is destroyed. In that state
    // risky operations are auto-denied (safe) instead of crashing.
    @Volatile
    private var approvalRequest: (suspend (AgentToolSpec, String) -> Boolean)? = null

    private var job: Job? = null

    fun bindActivity(
        vpnConsent: (suspend (Intent) -> Int)?,
        approval: (suspend (AgentToolSpec, String) -> Boolean)?,
    ) {
        vpnConsentRequest = vpnConsent
        approvalRequest = approval
    }

    val isRunning: Boolean
        get() = _state.value.running

    fun submit(
        context: Context,
        settings: AgentProviderSettings,
        history: List<AgentConversationMessage>,
        prompt: String,
        scenario: AgentScenario,
        assistantId: String,
        store: AgentConversationStore,
    ) {
        if (job?.isActive == true) return

        _state.value = RunState(
            running = true,
            prompt = prompt,
            scenario = scenario,
            messageId = assistantId,
            status = "正在连接 ${settings.model}…",
        )

        job = Global.launch {
            val trace = mutableListOf<AgentTraceEntry>()
            val executor = AndroidAgentToolExecutor(
                context = Global.application,
                startVpn = { startVpnWithConsent() },
                stopVpn = { Global.application.stopClashService() },
            )
            try {
                val finalText = AgentEngine().run(
                    settings = settings,
                    history = history,
                    prompt = prompt,
                    executor = executor,
                    approvalHandler = AgentApprovalHandler { tool, _, summary ->
                        // Auto-deny risky steps while no chat UI is present.
                        approvalRequest?.invoke(tool, summary) ?: false
                    },
                    scenario = scenario,
                    emit = { event ->
                        withContext(Dispatchers.Main.immediate) {
                            when (event) {
                                is AgentRunEvent.Thinking -> {
                                    trace += AgentTraceEntry("thinking", "正在思考 · 规划第 ${event.round} 步")
                                    _state.update { it.copy(trace = trace.toList(), status = "正在思考") }
                                }
                                is AgentRunEvent.Streaming -> {
                                    _state.update { it.copy(streamed = event.text, status = "正在生成回复") }
                                }
                                is AgentRunEvent.ToolStarted -> {
                                    trace += AgentTraceEntry("tool_start", event.summary, toolName = event.name)
                                    _state.update { it.copy(trace = trace.toList(), status = event.summary) }
                                }
                                is AgentRunEvent.ToolFinished -> {
                                    trace += AgentTraceEntry(
                                        if (event.success) "tool_done" else "tool_error",
                                        event.summary,
                                        toolName = event.name,
                                    )
                                    _state.update { it.copy(trace = trace.toList(), status = event.summary) }
                                }
                                is AgentRunEvent.Failed -> {
                                    trace += AgentTraceEntry("retry", event.message)
                                    _state.update { it.copy(trace = trace.toList(), status = event.message) }
                                }
                                is AgentRunEvent.Completed -> Unit
                            }
                        }
                    },
                )
                _state.update {
                    it.copy(running = false, streamed = finalText, status = "", error = null)
                }
            } catch (_: CancellationException) {
                _state.update { it.copy(running = false, status = "已停止", error = null) }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        running = false,
                        status = "",
                        error = error.message?.take(1200) ?: error.javaClass.simpleName,
                    )
                }
            }
            persistAssistant(store)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false, status = "已停止", error = null) }
    }

    fun clearPending() {
        if (job?.isActive != true) {
            _state.value = RunState()
        }
    }

    private fun persistAssistant(store: AgentConversationStore) {
        val id = _state.value.messageId ?: return
        val messages = store.load().toMutableList()
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = _state.value
        messages[index] = messages[index].copy(
            content = current.error?.let { "操作未完成：$it" } ?: current.streamed,
            trace = current.trace,
            running = false,
            isError = current.error != null,
        )
        store.save(messages)
    }

    private suspend fun startVpnWithConsent(): Boolean {
        val active = withProfile { queryActive() }
        if (active == null || !active.imported) return false

        val context = Global.application
        val request = context.startClashService()
        if (request != null) {
            val consent = vpnConsentRequest
            if (consent == null) return false
            if (withContext(Dispatchers.Main) { consent(request) } != Activity.RESULT_OK) return false
            context.startClashService()
        }
        repeat(100) {
            if (Remote.broadcasts.clashRunning) return true
            delay(100)
        }
        return false
    }
}
