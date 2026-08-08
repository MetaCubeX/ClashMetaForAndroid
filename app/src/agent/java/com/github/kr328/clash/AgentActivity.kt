package com.github.kr328.clash

import android.app.Activity
import android.content.DialogInterface
import android.net.Uri
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.agent.AgentChatAdapter
import com.github.kr328.clash.agent.AgentRunController
import com.github.kr328.clash.agent.AgentScreenDesign
import com.github.kr328.clash.agent.SmoothMarkdownStream
import com.github.kr328.clash.agent.authorization.AgentAuthorizationMode
import com.github.kr328.clash.agent.model.AgentConversationMessage
import com.github.kr328.clash.agent.model.AgentApiFormat
import com.github.kr328.clash.agent.model.AgentMessageRole
import com.github.kr328.clash.agent.model.AgentProviderSettings
import com.github.kr328.clash.agent.protocol.OpenAICompatibleClient
import com.github.kr328.clash.agent.runtime.AgentEngine.AgentScenario
import com.github.kr328.clash.agent.settings.AgentConversationStore
import com.github.kr328.clash.agent.settings.AgentSettingsStore
import com.github.kr328.clash.agent.tools.AgentToolSpec
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

class AgentActivity : BaseActivity<AgentScreenDesign>() {
    private val settingsStore by lazy { AgentSettingsStore(this) }
    private val conversationStore by lazy { AgentConversationStore(this) }
    private lateinit var adapter: AgentChatAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var input: TextInputEditText
    private lateinit var modelStatus: TextView
    private lateinit var suggestions: View
    private var smoothStream: SmoothMarkdownStream? = null
    private var streamJob: Job? = null
    private var streamingMessageId: String? = null
    private var followOutput = true
    private var scrollScheduled = false

    override suspend fun main() {
        val screen = AgentScreenDesign(this)
        setContentDesign(screen)
        bindViews(screen.root)
        updateModelStatus()
        AgentRunController.bindActivity(
            vpnConsent = { intent -> this@AgentActivity.startActivityForResultSuspend(intent) },
            approval = { tool, summary -> approve(tool, summary) },
        )

        // Resume rendering of a background run (e.g. user re-entered mid-run).
        observeRun()

        while (isActive) events.receive()
    }

    private fun bindViews(root: View) {
        recycler = root.findViewById(R.id.agent_messages)
        input = root.findViewById(R.id.agent_input)
        modelStatus = root.findViewById(R.id.agent_model_status)
        suggestions = root.findViewById(R.id.agent_empty)
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
        root.findViewById<View>(R.id.agent_stop).setOnClickListener { AgentRunController.cancel() }

        root.findViewById<View>(R.id.agent_suggest_create).setOnClickListener {
            submitPrompt("请从零开始帮我创建一份可用配置。", AgentScenario.CREATE)
        }
        root.findViewById<View>(R.id.agent_suggest_apps).setOnClickListener {
            submitPrompt("请读取我安装的应用，帮我规划应用级分流。", AgentScenario.APPS)
        }
        root.findViewById<View>(R.id.agent_suggest_diagnose).setOnClickListener {
            submitPrompt("请检查当前配置、VPN、网络、代理组、Provider 和活动连接状态，诊断明显问题并给出可执行建议。", AgentScenario.DIAGNOSE)
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

    private fun submitPrompt(prompt: String, scenario: AgentScenario = AgentScenario.GENERAL) {
        if (AgentRunController.isRunning) {
            Toast.makeText(this, R.string.agent_busy_send, Toast.LENGTH_SHORT).show()
            return
        }
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
        val assistantMessage = message(AgentMessageRole.ASSISTANT, "").copy(running = true)
        val assistantPosition = adapter.append(assistantMessage)
        streamingMessageId = assistantMessage.id
        followOutput = true
        updateSuggestionsVisibility()
        scrollToEnd()
        conversationStore.save(adapter.messages)

        AgentRunController.submit(
            context = this,
            settings = settings,
            history = history,
            prompt = prompt,
            scenario = scenario,
            assistantId = assistantMessage.id,
            store = conversationStore,
        )
        renderRunningUi()
    }

    private fun renderRunningUi() {
        val running = AgentRunController.isRunning
        design?.root?.findViewById<View>(R.id.agent_send)?.visibility = if (running) View.GONE else View.VISIBLE
        design?.root?.findViewById<View>(R.id.agent_stop)?.visibility = if (running) View.VISIBLE else View.GONE
        input.isEnabled = true
        design?.root?.findViewById<View>(R.id.agent_settings)?.isEnabled = true
    }

    private fun observeRun() {
        streamJob?.cancel()
        streamJob = launch {
            AgentRunController.state.collectLatest { state ->
                renderRunState(state)
            }
        }
    }

    private fun renderRunState(state: AgentRunController.RunState) {
        if (!::adapter.isInitialized) return
        renderRunningUi()

        val id = state.messageId ?: return
        val index = adapter.messages.indexOfFirst { it.id == id }
        if (index < 0) {
            // Run belongs to a session that was cleared; nothing to render.
            return
        }

        streamingMessageId = id
        val current = adapter.messages[index]
        val content = state.error?.let { getString(R.string.agent_run_failed, it) } ?: state.streamed

        // Always keep the trace panel in sync with the latest step, even while
        // text streaming owns the markdown updates.
        adapter.updateTrace(index, state.trace, state.running)

        val stream = smoothStream
        if (state.running) {
            if (stream == null) {
                smoothStream = SmoothMarkdownStream { visible ->
                    val pos = adapter.messages.indexOfFirst { it.id == id }
                    if (pos >= 0) {
                        // Preserve whatever trace updateTrace already stored; only
                        // the streamed text changes here.
                        adapter.replace(
                            pos,
                            adapter.messages[pos].copy(content = visible, running = true),
                            streaming = true,
                        )
                    }
                }.also { it.submit(state.streamed) }
            } else {
                stream.submit(state.streamed)
            }
        } else {
            stream?.cancel()
            smoothStream = null
            adapter.replace(
                index,
                current.copy(
                    content = content,
                    trace = state.trace,
                    running = false,
                    isError = state.error != null,
                ),
                streaming = false,
            )
            streamingMessageId = null
        }
    }


    private suspend fun startActivityForResultSuspend(intent: android.content.Intent): Int {
        return startActivityForResult(ActivityResultContracts.StartActivityForResult(), intent)
            .resultCode
    }

    private suspend fun approve(tool: AgentToolSpec, summary: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            val risk = getString(
                when (tool.risk.name) {
                    "CRITICAL" -> R.string.agent_risk_critical
                    "HIGH" -> R.string.agent_risk_high
                    "MEDIUM" -> R.string.agent_risk_medium
                    else -> R.string.agent_risk_normal
                }
            )
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.agent_approve_title)
                .setMessage("$summary\n\n$risk\n\n" + getString(R.string.agent_approve_scope))
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
        val current = settingsStore.load()
        val view = layoutInflater.inflate(R.layout.dialog_agent_settings, null, false)
        val baseUrl = view.findViewById<EditText>(R.id.agent_setting_base_url)
        val apiKey = view.findViewById<EditText>(R.id.agent_setting_api_key)
        val model = view.findViewById<EditText>(R.id.agent_setting_model)
        val apiFormat = view.findViewById<AutoCompleteTextView>(R.id.agent_setting_api_format)
        val authorization = view.findViewById<AutoCompleteTextView>(R.id.agent_setting_authorization)
        val apiKeyLayout = view.findViewById<TextInputLayout>(R.id.agent_layout_api_key)

        // The field uses textVisiblePassword to keep OEM "secure keyboards" away
        // (see the layout), which means the framework applies no masking of its
        // own. Apply it here, then re-initialise the end icon so the toggle
        // starts in the matching state — it reads the transformation when the
        // mode is set, and the mode was already applied during inflation.
        apiKey.transformationMethod = PasswordTransformationMethod.getInstance()
        apiKeyLayout.endIconMode = TextInputLayout.END_ICON_NONE
        apiKeyLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE

        // Helper text stays collapsed so the dialog reads as five clean fields,
        // and expands under whichever field the user is actually editing.
        view.findViewById<TextInputLayout>(R.id.agent_layout_base_url)
            .helperTextOnFocus(R.string.agent_base_url_helper)
        apiKeyLayout.helperTextOnFocus(R.string.agent_api_key_helper)
        view.findViewById<TextInputLayout>(R.id.agent_layout_model)
            .helperTextOnFocus(R.string.agent_model_helper)
        view.findViewById<TextInputLayout>(R.id.agent_layout_api_format)
            .helperTextOnFocus(R.string.agent_api_format_helper)
        view.findViewById<TextInputLayout>(R.id.agent_layout_authorization)
            .helperTextOnFocus(R.string.agent_authorization_helper)
        baseUrl.setText(current.baseUrl)
        apiKey.setText(current.apiKey)
        model.setText(current.model)
        apiFormat.setAdapter(ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            listOf(
                getString(R.string.agent_format_chat),
                getString(R.string.agent_format_responses),
            ),
        ))
        apiFormat.setText(
            if (current.apiFormat == AgentApiFormat.RESPONSES) {
                getString(R.string.agent_format_responses)
            } else {
                getString(R.string.agent_format_chat)
            },
            false,
        )
        // The dropdown carries the consequence, not just the name, so the
        // choice can be made without opening documentation.
        val authOptions = listOf(
            getString(R.string.agent_auth_cautious_detail),
            getString(R.string.agent_auth_balanced_detail),
            getString(R.string.agent_auth_full_detail),
        )
        authorization.setAdapter(ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            authOptions,
        ))
        authorization.setText(
            when (current.authorizationMode) {
                AgentAuthorizationMode.CAUTIOUS -> authOptions[0]
                AgentAuthorizationMode.BALANCED -> authOptions[1]
                AgentAuthorizationMode.FULL_AUTO -> authOptions[2]
            },
            false,
        )

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
                val candidate = readProviderSettings(baseUrl, apiKey, model, apiFormat, authorization, current)
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
                                .setTitle(R.string.agent_connect_failed)
                                .setMessage(error.message?.take(500) ?: error.javaClass.simpleName)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    testButton.isEnabled = true
                    testButton.setText(R.string.agent_test_connection)
                }
            }
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val candidate = readProviderSettings(baseUrl, apiKey, model, apiFormat, authorization, current)
                    ?: return@setOnClickListener
                runCatching { settingsStore.save(candidate) }.onFailure {
                    Toast.makeText(this, getString(R.string.agent_save_failed, it.message.orEmpty()), Toast.LENGTH_SHORT).show()
                }
                updateModelStatus()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /**
     * Shows [helper] under this field only while it holds focus. Toggling
     * helperTextEnabled (rather than just the text) also releases the row of
     * space Material reserves for it, so the collapsed dialog stays compact.
     */
    private fun TextInputLayout.helperTextOnFocus(@StringRes helper: Int) {
        val field = editText ?: return
        isHelperTextEnabled = false
        field.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                helperText = context.getString(helper)
            } else {
                helperText = null
                isHelperTextEnabled = false
            }
        }
    }

    private fun readProviderSettings(
        baseUrl: EditText,
        apiKey: EditText,
        model: EditText,
        apiFormat: AutoCompleteTextView,
        authorization: AutoCompleteTextView,
        current: AgentProviderSettings,
    ): AgentProviderSettings? {
        val normalizedUrl = baseUrl.text.toString().trim()
        val key = apiKey.text.toString().trim()
        val modelName = model.text.toString().trim()
        if (!normalizedUrl.startsWith("https://") && !normalizedUrl.startsWith("http://")) {
            baseUrl.error = getString(R.string.agent_error_url_scheme)
            return null
        }
        val host = runCatching { Uri.parse(normalizedUrl).host.orEmpty() }.getOrDefault("")
        if (normalizedUrl.startsWith("http://") && key.isNotEmpty() &&
            host !in setOf("localhost", "127.0.0.1", "::1")) {
            apiKey.error = getString(R.string.agent_error_insecure_key)
            return null
        }
        if (modelName.isEmpty()) {
            model.error = getString(R.string.agent_error_model_empty)
            return null
        }
        val mode = when (authorization.text?.toString()) {
            getString(R.string.agent_auth_cautious_detail) -> AgentAuthorizationMode.CAUTIOUS
            getString(R.string.agent_auth_full_detail) -> AgentAuthorizationMode.FULL_AUTO
            else -> AgentAuthorizationMode.BALANCED
        }
        val format = if (apiFormat.text?.toString() == getString(R.string.agent_format_responses)) {
            AgentApiFormat.RESPONSES
        } else {
            AgentApiFormat.CHAT_COMPLETIONS
        }
        return AgentProviderSettings(
            normalizedUrl, modelName, key, format, mode, current.maxToolRounds
        )
    }

    private fun confirmClear() {
        if (AgentRunController.isRunning || adapter.messages.isEmpty()) {
            if (AgentRunController.isRunning) {
                Toast.makeText(this, R.string.agent_busy_clear, Toast.LENGTH_SHORT).show()
            }
            return
        }
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.agent_clear_confirm)
            .setNegativeButton(R.string.agent_cancel, null)
            .setPositiveButton(R.string.agent_clear) { _, _ ->
                adapter.clear()
                conversationStore.clear()
                AgentRunController.clearPending()
                updateSuggestionsVisibility()
            }
            .show()
    }

    private fun updateModelStatus() {
        if (!::modelStatus.isInitialized) return
        val settings = settingsStore.load()
        modelStatus.text = if (settings.isConfigured) {
            "${settings.model} · " + getString(
                when (settings.authorizationMode) {
                    AgentAuthorizationMode.CAUTIOUS -> R.string.agent_status_auth_cautious
                    AgentAuthorizationMode.BALANCED -> R.string.agent_status_auth_balanced
                    AgentAuthorizationMode.FULL_AUTO -> R.string.agent_status_auth_full
                }
            )
        } else getString(R.string.agent_not_configured)
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
        streamJob?.cancel()
        smoothStream?.cancel()
        AgentRunController.bindActivity(null, null)
        if (::adapter.isInitialized) adapter.close()
        super.onDestroy()
    }

    private fun message(role: AgentMessageRole, content: String, isError: Boolean = false) =
        AgentConversationMessage(UUID.randomUUID().toString(), role, content, isError = isError)
}
