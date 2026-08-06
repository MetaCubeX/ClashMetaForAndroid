package com.github.kr328.clash

import android.app.Activity
import android.content.DialogInterface
import android.net.Uri
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.agent.AgentChatAdapter
import com.github.kr328.clash.agent.AgentScreenDesign
import com.github.kr328.clash.agent.AndroidAgentToolExecutor
import com.github.kr328.clash.agent.SmoothMarkdownStream
import com.github.kr328.clash.agent.authorization.AgentAuthorizationMode
import com.github.kr328.clash.agent.model.AgentConversationMessage
import com.github.kr328.clash.agent.model.AgentMessageRole
import com.github.kr328.clash.agent.model.AgentProviderSettings
import com.github.kr328.clash.agent.model.AgentRunEvent
import com.github.kr328.clash.agent.protocol.OpenAICompatibleClient
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
    private lateinit var suggestions: View
    private var streamingMessageId: String? = null
    private var followOutput = true
    private var scrollScheduled = false

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
        suggestions = root.findViewById(R.id.agent_suggestions_container)
        adapter = AgentChatAdapter(this, conversationStore.load().toMutableList()) { messageId ->
            if (followOutput && messageId == streamingMessageId) scheduleScrollToEnd()
        }
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.itemAnimator = null
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> followOutput = false
                    RecyclerView.SCROLL_STATE_IDLE -> followOutput = isNearBottom()
                }
            }
        })
        updateSuggestionsVisibility()
        scrollToEnd()

        root.findViewById<View>(R.id.agent_back).setOnClickListener { finish() }
        root.findViewById<View>(R.id.agent_settings).setOnClickListener { showSettings() }
        root.findViewById<View>(R.id.agent_clear).setOnClickListener { confirmClear() }
        root.findViewById<View>(R.id.agent_send).setOnClickListener { sendCurrentMessage() }
        root.findViewById<View>(R.id.agent_stop).setOnClickListener { generation?.cancel() }

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
        val assistantMessage = message(AgentMessageRole.ASSISTANT, "")
        val assistantPosition = adapter.append(assistantMessage)
        streamingMessageId = assistantMessage.id
        followOutput = true
        updateSuggestionsVisibility()
        scrollToEnd()
        setRunning(true, "正在连接 ${settings.model}…")

        generation = launch {
            val smoothStream = SmoothMarkdownStream { visibleText ->
                adapter.replace(
                    assistantPosition,
                    assistantMessage.copy(content = visibleText),
                    streaming = true,
                )
            }
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
                        is AgentRunEvent.Thinking -> progressText.text = "正在思考 · 规划第 ${event.round} 步…"
                        is AgentRunEvent.Streaming -> {
                            progressText.text = "正在生成回复…"
                            smoothStream.submit(event.text)
                        }
                        is AgentRunEvent.ToolStarted -> progressText.text = event.summary
                        is AgentRunEvent.ToolFinished -> progressText.text =
                            (if (event.success) "✓ " else "⚠ ") + event.summary
                        is AgentRunEvent.Failed -> progressText.text = event.message
                        is AgentRunEvent.Completed -> Unit
                    }
                } }
                smoothStream.finish(finalText)
                adapter.replace(assistantPosition, assistantMessage.copy(content = finalText))
            } catch (_: CancellationException) {
                val preserved = smoothStream.currentText().trimEnd()
                val content = if (preserved.isBlank()) {
                    "已停止本次操作。"
                } else {
                    "$preserved\n\n> 已停止本次操作。"
                }
                adapter.replace(assistantPosition, assistantMessage.copy(content = content))
            } catch (error: Throwable) {
                val detail = error.message?.take(1200) ?: error.javaClass.simpleName
                adapter.replace(
                    assistantPosition,
                    assistantMessage.copy(content = "操作未完成：$detail", isError = true),
                )
            } finally {
                smoothStream.cancel()
                conversationStore.save(adapter.messages)
                setRunning(false, "")
                if (followOutput) scrollToEnd()
                streamingMessageId = null
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

        val horizontalMargin = (24 * resources.displayMetrics.density).toInt()
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agent_settings)
            .setView(view, horizontalMargin, 0, horizontalMargin, 0)
            .setNegativeButton(R.string.agent_cancel, null)
            .setNeutralButton(R.string.agent_test_connection, null)
            .setPositiveButton(R.string.agent_save, null)
            .create()
        var testJob: Job? = null
        dialog.setOnShowListener {
            val testButton = dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
            testButton.setOnClickListener {
                val candidate = readProviderSettings(baseUrl, apiKey, model, authorization, current)
                    ?: return@setOnClickListener
                testButton.isEnabled = false
                testButton.setText(R.string.agent_testing_connection)
                testJob = launch {
                    runCatching { OpenAICompatibleClient().testConnection(candidate) }
                        .onSuccess {
                            Toast.makeText(this@AgentActivity, R.string.agent_test_success, Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { error ->
                            MaterialAlertDialogBuilder(this@AgentActivity)
                                .setTitle("连接失败")
                                .setMessage(error.message?.take(500) ?: error.javaClass.simpleName)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    testButton.isEnabled = true
                    testButton.setText(R.string.agent_test_connection)
                }
            }
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val candidate = readProviderSettings(baseUrl, apiKey, model, authorization, current)
                    ?: return@setOnClickListener
                runCatching { settingsStore.save(candidate) }.onFailure {
                    apiKey.error = it.message ?: "API Key 保存失败"
                    return@setOnClickListener
                }
                updateModelStatus()
                dialog.dismiss()
                if (input.text?.isNotBlank() == true) input.requestFocus()
            }
        }
        dialog.setOnDismissListener { testJob?.cancel() }
        dialog.show()
    }

    private fun readProviderSettings(
        baseUrl: EditText,
        apiKey: EditText,
        model: EditText,
        authorization: RadioGroup,
        current: AgentProviderSettings,
    ): AgentProviderSettings? {
        val normalizedUrl = baseUrl.text.toString().trim()
        val key = apiKey.text.toString().trim()
        val modelName = model.text.toString().trim()
        if (!normalizedUrl.startsWith("https://") && !normalizedUrl.startsWith("http://")) {
            baseUrl.error = "请输入 http:// 或 https:// 地址"
            return null
        }
        val host = runCatching { Uri.parse(normalizedUrl).host.orEmpty() }.getOrDefault("")
        if (normalizedUrl.startsWith("http://") && key.isNotEmpty() &&
            host !in setOf("localhost", "127.0.0.1", "::1")) {
            apiKey.error = "为防止密钥泄露，非本机地址请使用 HTTPS"
            return null
        }
        if (modelName.isEmpty()) {
            model.error = "请输入模型名称"
            return null
        }
        val mode = when (authorization.checkedRadioButtonId) {
            R.id.agent_auth_cautious -> AgentAuthorizationMode.CAUTIOUS
            R.id.agent_auth_full -> AgentAuthorizationMode.FULL_AUTO
            else -> AgentAuthorizationMode.BALANCED
        }
        return AgentProviderSettings(normalizedUrl, modelName, key, mode, current.maxToolRounds)
    }

    private fun confirmClear() {
        if (generation?.isActive == true || adapter.messages.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.agent_clear_confirm)
            .setNegativeButton(R.string.agent_cancel, null)
            .setPositiveButton(R.string.agent_clear) { _, _ ->
                adapter.clear()
                conversationStore.clear()
                updateSuggestionsVisibility()
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

    private fun scheduleScrollToEnd() {
        if (scrollScheduled || !::recycler.isInitialized) return
        scrollScheduled = true
        recycler.postOnAnimation {
            scrollScheduled = false
            if (followOutput && adapter.itemCount > 0) {
                recycler.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    private fun isNearBottom(): Boolean {
        if (!::recycler.isInitialized || adapter.itemCount == 0) return true
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return true
        return manager.findLastVisibleItemPosition() >= adapter.itemCount - 2
    }

    private fun updateSuggestionsVisibility() {
        if (::suggestions.isInitialized) {
            suggestions.visibility = if (adapter.messages.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroy() {
        if (::adapter.isInitialized) adapter.close()
        super.onDestroy()
    }

    private fun message(role: AgentMessageRole, content: String, isError: Boolean = false) =
        AgentConversationMessage(UUID.randomUUID().toString(), role, content, isError = isError)

}
