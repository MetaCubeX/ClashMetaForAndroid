package com.github.kr328.clash

import android.app.Activity
import android.content.DialogInterface
import android.os.SystemClock
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.agent.AgentChatAdapter
import com.github.kr328.clash.agent.AgentScreenDesign
import com.github.kr328.clash.agent.AndroidAgentToolExecutor
import com.github.kr328.clash.agent.authorization.AgentAuthorizationMode
import com.github.kr328.clash.agent.model.AgentConversationMessage
import com.github.kr328.clash.agent.model.AgentMessageRole
import com.github.kr328.clash.agent.model.AgentProviderSettings
import com.github.kr328.clash.agent.model.AgentRunEvent
import com.github.kr328.clash.agent.runtime.AgentApprovalHandler
import com.github.kr328.clash.agent.runtime.AgentEngine
import com.github.kr328.clash.agent.settings.AgentConversationStore
import com.github.kr328.clash.agent.settings.AgentSettingsStore
import com.github.kr328.clash.agent.tools.AgentToolSpec
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume

class AgentActivity : BaseActivity<AgentScreenDesign>() {
    private val settingsStore by lazy { AgentSettingsStore(this) }
    private val conversationStore by lazy { AgentConversationStore(this) }
    private var generation: Job? = null
    private lateinit var adapter: AgentChatAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var input: TextInputEditText
    private lateinit var modelStatus: TextView
    private lateinit var progressRow: View
    private lateinit var progressText: TextView

    override suspend fun main() {
        val screen = AgentScreenDesign(this)
        setContentDesign(screen)
        bindViews(screen.root)
        updateModelStatus()

        while (isActive) events.receive()
    }

    private fun bindViews(root: View) {
        recycler = root.findViewById(R.id.agent_messages)
        input = root.findViewById(R.id.agent_input)
        modelStatus = root.findViewById(R.id.agent_model_status)
        progressRow = root.findViewById(R.id.agent_progress_row)
        progressText = root.findViewById(R.id.agent_progress_text)
        adapter = AgentChatAdapter(this, conversationStore.load().toMutableList())
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter
        scrollToEnd()

        root.findViewById<View>(R.id.agent_back).setOnClickListener { finish() }
        root.findViewById<View>(R.id.agent_settings).setOnClickListener { showSettings() }
        root.findViewById<View>(R.id.agent_clear).setOnClickListener { confirmClear() }
        root.findViewById<View>(R.id.agent_send).setOnClickListener { sendCurrentMessage() }
        root.findViewById<View>(R.id.agent_stop).setOnClickListener { generation?.cancel() }
        input.setOnEditorActionListener { _, actionId, event ->
            val isSend = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN && !event.isShiftPressed)
            if (isSend) sendCurrentMessage()
            isSend
        }

        root.findViewById<View>(R.id.agent_suggest_create).setOnClickListener {
            submitPrompt("请从零开始帮我创建一份可用配置。先了解我的节点来源、使用地区和分流需求；如果没有节点，先创建安全的 DIRECT/REJECT 基础配置。")
        }
        root.findViewById<View>(R.id.agent_suggest_apps).setOnClickListener {
            submitPrompt("请读取我安装的应用，帮我规划应用级分流。先列出建议分类并询问我的偏好，确认后再修改配置。")
        }
        root.findViewById<View>(R.id.agent_suggest_diagnose).setOnClickListener {
            submitPrompt("请检查当前配置、VPN、网络、代理组、Provider 和活动连接状态，诊断明显问题并给出可执行建议；只读检查不需要询问。")
        }
        if (!settingsStore.load().isConfigured) root.post { showSettings() }
    }

    private fun sendCurrentMessage() {
        val text = input.text?.toString().orEmpty().trim()
        if (text.isEmpty()) return
        input.setText("")
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(input.windowToken, 0)
        submitPrompt(text)
    }

    private fun submitPrompt(prompt: String) {
        if (generation?.isActive == true) return
        val settings = settingsStore.load()
        if (!settings.isConfigured) {
            input.setText(prompt)
            input.setSelection(input.text?.length ?: 0)
            showSettings()
            return
        }

        val history = adapter.messages.toList()
        val userMessage = message(AgentMessageRole.USER, prompt)
        adapter.append(userMessage)
        val assistantPosition = adapter.append(message(AgentMessageRole.ASSISTANT, "正在思考…"))
        scrollToEnd()
        setRunning(true, "正在连接 ${settings.model}…")

        generation = launch {
            var lastRendered = 0L
            try {
                val executor = AndroidAgentToolExecutor(
                    context = this@AgentActivity,
                    startVpn = { startVpnFromAgent() },
                    stopVpn = { stopClashService() },
                )
                val finalText = AgentEngine().run(
                    settings = settings,
                    history = history,
                    prompt = prompt,
                    executor = executor,
                    approvalHandler = AgentApprovalHandler { tool, _, summary -> approve(tool, summary) },
                ) { event -> withContext(Dispatchers.Main.immediate) {
                    when (event) {
                        is AgentRunEvent.Thinking -> progressText.text = "正在规划第 ${event.round} 步…"
                        is AgentRunEvent.Streaming -> {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastRendered >= 40L || event.text.length < 80) {
                                adapter.replace(assistantPosition, message(AgentMessageRole.ASSISTANT, event.text))
                                scrollToEnd()
                                lastRendered = now
                            }
                        }
                        is AgentRunEvent.ToolStarted -> progressText.text = event.summary
                        is AgentRunEvent.ToolFinished -> progressText.text =
                            (if (event.success) "✓ " else "⚠ ") + event.summary
                        is AgentRunEvent.Failed -> progressText.text = event.message
                        is AgentRunEvent.Completed -> Unit
                    }
                } }
                adapter.replace(assistantPosition, message(AgentMessageRole.ASSISTANT, finalText))
            } catch (_: CancellationException) {
                adapter.replace(assistantPosition, message(AgentMessageRole.ASSISTANT, "已停止本次操作。"))
            } catch (error: Throwable) {
                val detail = error.message?.take(1200) ?: error.javaClass.simpleName
                adapter.replace(assistantPosition, message(AgentMessageRole.ASSISTANT, "操作未完成：$detail", isError = true))
            } finally {
                conversationStore.save(adapter.messages)
                setRunning(false, "")
                scrollToEnd()
            }
        }
    }

    private suspend fun approve(tool: AgentToolSpec, summary: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            val risk = when (tool.risk.name) {
                "CRITICAL" -> "严重：此操作可能不可逆"
                "HIGH" -> "高风险：会改变配置或运行状态"
                "MEDIUM" -> "中等风险：会更新本地或远程状态"
                else -> "常规操作"
            }
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.agent_approve_title)
                .setMessage("$summary\n\n$risk\n\nAI 只会获得本次操作的授权。")
                .setNegativeButton(R.string.agent_deny) { _, _ ->
                    if (continuation.isActive) continuation.resume(false)
                }
                .setPositiveButton(R.string.agent_allow_once) { _, _ ->
                    if (continuation.isActive) continuation.resume(true)
                }
                .setOnCancelListener {
                    if (continuation.isActive) continuation.resume(false)
                }
                .show()
            continuation.invokeOnCancellation { dialog.dismiss() }
        }

    private fun showSettings() {
        if (generation?.isActive == true) return
        val current = settingsStore.load()
        val view = layoutInflater.inflate(R.layout.dialog_agent_settings, null, false)
        val baseUrl = view.findViewById<EditText>(R.id.agent_setting_base_url)
        val apiKey = view.findViewById<EditText>(R.id.agent_setting_api_key)
        val model = view.findViewById<EditText>(R.id.agent_setting_model)
        val authorization = view.findViewById<RadioGroup>(R.id.agent_setting_authorization)
        baseUrl.setText(current.baseUrl)
        apiKey.setText(current.apiKey)
        model.setText(current.model)
        authorization.check(when (current.authorizationMode) {
            AgentAuthorizationMode.CAUTIOUS -> R.id.agent_auth_cautious
            AgentAuthorizationMode.BALANCED -> R.id.agent_auth_balanced
            AgentAuthorizationMode.FULL_AUTO -> R.id.agent_auth_full
        })

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agent_settings)
            .setView(view)
            .setNegativeButton(R.string.agent_cancel, null)
            .setPositiveButton(R.string.agent_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val normalizedUrl = baseUrl.text.toString().trim()
                val key = apiKey.text.toString().trim()
                val modelName = model.text.toString().trim()
                if (!normalizedUrl.startsWith("https://") && !normalizedUrl.startsWith("http://")) {
                    baseUrl.error = "请输入 http:// 或 https:// 地址"
                    return@setOnClickListener
                }
                val host = runCatching { Uri.parse(normalizedUrl).host.orEmpty() }.getOrDefault("")
                if (normalizedUrl.startsWith("http://") && key.isNotEmpty() &&
                    host !in setOf("localhost", "127.0.0.1", "::1")) {
                    apiKey.error = "为防止密钥泄露，非本机地址请使用 HTTPS"
                    return@setOnClickListener
                }
                if (modelName.isEmpty()) {
                    model.error = "请输入模型名称"
                    return@setOnClickListener
                }
                val mode = when (authorization.checkedRadioButtonId) {
                    R.id.agent_auth_cautious -> AgentAuthorizationMode.CAUTIOUS
                    R.id.agent_auth_full -> AgentAuthorizationMode.FULL_AUTO
                    else -> AgentAuthorizationMode.BALANCED
                }
                runCatching {
                    settingsStore.save(AgentProviderSettings(normalizedUrl, modelName, key, mode, current.maxToolRounds))
                }.onFailure {
                    apiKey.error = it.message ?: "API Key 保存失败"
                    return@setOnClickListener
                }
                updateModelStatus()
                dialog.dismiss()
                if (input.text?.isNotBlank() == true) input.requestFocus()
            }
        }
        dialog.show()
    }

    private fun confirmClear() {
        if (generation?.isActive == true || adapter.messages.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.agent_clear_confirm)
            .setNegativeButton(R.string.agent_cancel, null)
            .setPositiveButton(R.string.agent_clear) { _, _ ->
                adapter.clear()
                conversationStore.clear()
            }
            .show()
    }

    private suspend fun startVpnFromAgent(): Boolean {
        val active = withProfile { queryActive() }
        if (active == null || !active.imported) return false
        val request = startClashService()
        if (request != null) {
            val result = startActivityForResult(ActivityResultContracts.StartActivityForResult(), request)
            if (result.resultCode != Activity.RESULT_OK) return false
            startClashService()
        }
        repeat(100) {
            if (Remote.broadcasts.clashRunning) return true
            delay(100)
        }
        return false
    }

    private fun updateModelStatus() {
        if (!::modelStatus.isInitialized) return
        val settings = settingsStore.load()
        modelStatus.text = if (settings.isConfigured) {
            "${settings.model} · ${when (settings.authorizationMode) {
                AgentAuthorizationMode.CAUTIOUS -> "谨慎授权"
                AgentAuthorizationMode.BALANCED -> "均衡授权"
                AgentAuthorizationMode.FULL_AUTO -> "全部自动放行"
            }}"
        } else getString(R.string.agent_not_configured)
    }

    private fun setRunning(running: Boolean, status: String) {
        progressRow.visibility = if (running) View.VISIBLE else View.GONE
        progressText.text = status
        input.isEnabled = !running
        design?.root?.findViewById<View>(R.id.agent_send)?.isEnabled = !running
        design?.root?.findViewById<View>(R.id.agent_settings)?.isEnabled = !running
    }

    private fun scrollToEnd() {
        if (::adapter.isInitialized && adapter.itemCount > 0) recycler.post {
            recycler.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun message(role: AgentMessageRole, content: String, isError: Boolean = false) =
        AgentConversationMessage(UUID.randomUUID().toString(), role, content, isError = isError)
}
