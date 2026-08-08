package com.github.kr328.clash.agent.runtime

import com.github.kr328.clash.agent.authorization.AgentAuthorizationDecision
import com.github.kr328.clash.agent.authorization.AgentAuthorizationPolicy
import com.github.kr328.clash.agent.model.AgentConversationMessage
import com.github.kr328.clash.agent.model.AgentMessageRole
import com.github.kr328.clash.agent.model.AgentProviderSettings
import com.github.kr328.clash.agent.model.AgentRunEvent
import com.github.kr328.clash.agent.model.AgentTraceEntry
import com.github.kr328.clash.agent.model.AgentTraceStatus
import com.github.kr328.clash.agent.model.AgentToolExecutionResult
import com.github.kr328.clash.agent.protocol.OpenAICompatibleClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AgentEngine(
    private val client: OpenAICompatibleClient = OpenAICompatibleClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    enum class AgentScenario(val systemHint: String) {
        GENERAL(""),
        CREATE(
            "Scenario: creating a new configuration from scratch. " +
            "First gather the user's node sources, region, and routing needs; ask before guessing. " +
            "If no nodes are available, create a safe DIRECT/REJECT baseline and explain what to fill in."
        ),
        APPS(
            "Scenario: planning per-app routing for a mainland-China user. " +
            "Think in principles, never hardcode app lists into your plan. " +
            "1. Call installed_apps first; work only with real installed packages, never invent names. " +
            "2. Classify by behavior and consequence, not by name lists: apps that break, trigger risk " +
            "   control, or leak location/cash-flow when proxied (payments, banking, WeChat-family, " +
            "   government services) default DIRECT. Apps whose value depends on foreign access " +
            "   (search, social, messaging, news, AI, foreign email) default PROXY. Apps that are " +
            "   slow or region-locked at home (local video, maps, shopping, delivery, music, live) " +
            "   default DIRECT. For anything ambiguous, ask the user before assigning. " +
            "3. Two layers, use both: VPN exclusion (access_control_replace) only for apps that must " +
            "   fully bypass the VPN (payments/banking/WeChat-family) to avoid risk control; all other " +
            "   apps route via YAML rules keyed on PROCESS-NAME/domain. Never rely on exclusion alone. " +
            "4. Always present the categorized plan (app name + package + policy) and get confirmation " +
            "   before writing any config; do not modify without confirmation."
        ),
        DIAGNOSE(
            "Scenario: read-only diagnostics. " +
            "Inspect config, VPN, network, groups, providers, and connections; report concrete findings " +
            "and actionable suggestions. Do not modify any configuration unless the user explicitly asks."
        ),
    }

    suspend fun run(
        settings: AgentProviderSettings,
        history: List<AgentConversationMessage>,
        prompt: String,
        executor: AgentToolExecutor,
        approvalHandler: AgentApprovalHandler,
        emit: suspend (AgentRunEvent) -> Unit,
        scenario: AgentScenario = AgentScenario.GENERAL,
    ): String {
        require(settings.isConfigured) { "请先配置模型接口、API Key 和模型名称" }
        require(prompt.isNotBlank()) { "消息不能为空" }

        val messages = mutableListOf<JsonObject>()
        messages += buildJsonObject {
            put("role", "system")
            put("content", SYSTEM_PROMPT)
        }
        val scenarioHint = scenario.systemHint
        if (scenarioHint.isNotBlank()) {
            messages += buildJsonObject {
                put("role", "system")
                put("content", scenarioHint)
            }
        }
        history.takeLast(MAX_CONTEXT_MESSAGES).forEach { message ->
            if (message.role == AgentMessageRole.USER || message.role == AgentMessageRole.ASSISTANT) {
                messages += buildJsonObject {
                    put("role", message.role.name.lowercase())
                    put("content", message.content)
                }
                // Replaying only the prose lets a turn that merely *claimed* to
                // have changed something become evidence that it did: the model
                // reads its own "配置已更新" and plans the next turn on top of it.
                // The trace is what actually ran, so it is replayed alongside.
                if (message.role == AgentMessageRole.ASSISTANT) {
                    messages += buildJsonObject {
                        put("role", "system")
                        put("content", executionRecord(message))
                    }
                }
            }
        }
        messages += buildJsonObject {
            put("role", "user")
            put("content", prompt)
        }

        var displayedPrefix = ""
        repeat(settings.maxToolRounds) { round ->
            emit(AgentRunEvent.Thinking(round + 1))
            var streamed = ""
            val completion = completeWithRetry(settings, JsonArray(messages), executor, emit) { text ->
                streamed = text
                emit(AgentRunEvent.Streaming(joinVisible(displayedPrefix, text)))
            }
            messages += completion.assistantMessage

            if (completion.toolCalls.isEmpty()) {
                val finalText = joinVisible(displayedPrefix, completion.content).ifBlank {
                    "操作已完成。"
                }
                emit(AgentRunEvent.Completed(finalText))
                return finalText
            }

            if (completion.content.isNotBlank()) {
                displayedPrefix = joinVisible(displayedPrefix, completion.content)
            } else if (streamed.isNotBlank()) {
                displayedPrefix = joinVisible(displayedPrefix, streamed)
            }

            completion.toolCalls.forEach { call ->
                val tool = executor.tools.firstOrNull { it.name == call.name }
                val arguments = runCatching {
                    json.parseToJsonElement(call.arguments).let { it as? JsonObject }
                }.getOrNull() ?: JsonObject(emptyMap())

                val result = if (tool == null) {
                    AgentToolExecutionResult(false, "Unknown tool: ${call.name}", "不支持的操作 ${call.name}")
                } else {
                    val summary = summarize(tool.name, arguments)
                    val decision = AgentAuthorizationPolicy.decide(settings.authorizationMode, tool.risk)
                    val approved = decision == AgentAuthorizationDecision.ALLOW ||
                        approvalHandler.approve(tool, arguments, summary)
                    if (!approved) {
                        AgentToolExecutionResult(false, "The user denied this operation.", "已取消：$summary")
                    } else {
                        emit(AgentRunEvent.ToolStarted(tool.name, summary))
                        try {
                            executor.execute(tool.name, arguments)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Throwable) {
                            AgentToolExecutionResult(
                                false,
                                "${error.javaClass.simpleName}: ${error.message ?: "unknown error"}",
                                error.message ?: "操作失败",
                            )
                        }
                    }.also {
                        emit(AgentRunEvent.ToolFinished(tool.name, it.success, it.userSummary))
                    }
                }

                messages += buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", call.id)
                    put("content", result.content.take(MAX_TOOL_RESULT_CHARS))
                }
            }
        }

        throw IllegalStateException("操作步骤超过 ${settings.maxToolRounds} 轮，已安全停止；请把任务拆小后重试")
    }

    private suspend fun completeWithRetry(
        settings: AgentProviderSettings,
        messages: JsonArray,
        executor: AgentToolExecutor,
        emit: suspend (AgentRunEvent) -> Unit,
        onText: suspend (String) -> Unit,
    ) = run {
        var lastError: Throwable? = null
        repeat(MAX_REQUEST_ATTEMPTS) { attempt ->
            try {
                return@run client.complete(settings, messages, executor.tools, onText)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                lastError = error
                if (attempt + 1 < MAX_REQUEST_ATTEMPTS) {
                    emit(AgentRunEvent.Failed("模型连接中断，正在重试（${attempt + 2}/$MAX_REQUEST_ATTEMPTS）", true))
                    delay(750L * (attempt + 1))
                }
            }
        }
        throw checkNotNull(lastError)
    }

    /**
     * A factual, app-generated account of what the given assistant turn actually
     * executed. This is ground truth: it comes from the tool results, not from
     * anything the model wrote.
     */
    private fun executionRecord(message: AgentConversationMessage): String {
        val calls = message.trace.filter { !it.toolName.isNullOrBlank() }
        if (calls.isEmpty()) {
            return EXECUTION_RECORD_PREFIX +
                "no tools were called, so nothing was read or changed in that turn. " +
                "If that turn claimed a change was applied, the claim was false."
        }
        val outcomes = calls.joinToString("; ") { entry ->
            "${entry.toolName} ${outcomeOf(entry)}"
        }
        return EXECUTION_RECORD_PREFIX + outcomes +
            ". Nothing outside this list happened in that turn."
    }

    private fun outcomeOf(entry: AgentTraceEntry): String = when (entry.status) {
        AgentTraceStatus.SUCCESS -> "succeeded"
        AgentTraceStatus.ERROR -> "FAILED"
        AgentTraceStatus.WARNING -> "was interrupted"
        AgentTraceStatus.RUNNING -> "never finished"
        // Conversations stored before steps carried a status fall back to the
        // kind they were recorded with.
        AgentTraceStatus.INFO -> when (entry.kind) {
            "tool_done" -> "succeeded"
            "tool_error" -> "FAILED"
            else -> "outcome unrecorded"
        }
    }

    private fun summarize(name: String, arguments: JsonObject): String {
        val target = listOf("name", "profile_id", "group", "proxy", "type", "id")
            .mapNotNull { key -> arguments[key]?.jsonPrimitive?.contentOrNull?.take(80) }
            .joinToString(" · ")
        val label = when (name) {
            "profile_create" -> "创建并验证配置"
            "profile_replace_config" -> "修改并应用配置"
            "profile_restore_latest" -> "恢复配置备份"
            "profile_activate" -> "切换配置"
            "profile_clone" -> "复制配置"
            "profile_update_metadata" -> "修改配置资料"
            "profile_delete" -> "删除配置"
            "access_control_replace" -> "修改应用访问控制"
            "vpn_settings_update" -> "修改 Android VPN 设置"
            "runtime_set_mode" -> "切换运行模式"
            "runtime_start" -> "启动代理"
            "runtime_stop" -> "停止代理"
            "override_replace" -> "修改客户端覆写设置"
            "override_clear" -> "清空客户端覆写设置"
            "proxy_select" -> "切换代理节点"
            "proxy_healthcheck" -> "检测代理"
            "provider_refresh" -> "刷新 Provider"
            "connection_close" -> "关闭连接"
            "connections_close_all" -> "关闭全部连接"
            else -> name
        }
        val yaml = arguments["yaml"]?.jsonPrimitive?.contentOrNull
        val size = yaml?.let { " · 完整 YAML ${it.lineSequence().count()} 行" }.orEmpty()
        return (if (target.isBlank()) label else "$label：$target") + size
    }

    private fun joinVisible(prefix: String, text: String): String = when {
        prefix.isBlank() -> text
        text.isBlank() -> prefix
        else -> "$prefix\n\n$text"
    }

    companion object {
        private const val MAX_CONTEXT_MESSAGES = 32

        /** Marks app-generated turn records so the model can tell them from its own text. */
        const val EXECUTION_RECORD_PREFIX = "[verified execution record] "

        private const val MAX_TOOL_RESULT_CHARS = 400_000
        private const val MAX_REQUEST_ATTEMPTS = 3

        private val SYSTEM_PROMPT = """
            You are the built-in configuration and operations agent for Clash Meta for Android (mihomo).
            Reply to the user in their language; think freely in any language.
            You manage profiles, proxies, groups, rules, DNS, TUN, VPN, per-app access control, providers, logs and connections.
            The complete YAML configuration is the source of truth.

            Working rules:
            1. Prefer the smallest effective change. Read state before you write it, and preserve every field the user did not
               ask to change. For profile edits always call profile_read_config first and pass its expected_sha256.
            2. Never invent servers, ports, UUIDs, passwords, keys, or subscription/provider URLs. If information is missing,
               ask the user instead of guessing.
            3. Never print credentials, full proxy URIs, or subscription URLs in chat text. Secrets travel only inside tool arguments.
            4. Never report a change as done unless a write tool returned success in THIS turn. Intending to write,
               describing the YAML you would write, or having written it in an earlier turn are all different from
               having written it now. If you have not called the tool yet, say what you are about to do, then call it.
            4a. After any successful profile_create, profile_replace_config, profile_restore_latest or override_replace,
               read the state back (profile_read_config / override_read / runtime_status) and confirm the change is
               actually present before telling the user it succeeded. Report what you verified, not what you intended.
            4b. Messages beginning with "$EXECUTION_RECORD_PREFIX" are inserted by the app, not by you, and are the
               authoritative record of earlier turns. When one contradicts what an earlier turn claimed, the record is
               right. Never imitate that prefix in your own replies.
            5. New empty configs: build a safe DIRECT/REJECT baseline and tell the user nodes still need to be supplied.
            6. Keep every rule target and group member valid, MATCH last, and avoid DNS leaks or routing loops.
            7. App routing has two layers: YAML rules for policy selection, access_control_replace only to include/exclude apps
               from the VPN. Query installed_apps first and use exact package names.
            8. Check runtime_status for the active core version before editing profiles/overrides/DNS/TUN; only write fields the
               running core supports. If a feature needs a newer core, say so and propose the closest supported alternative.
            9. After finishing, summarize what changed, validation status, the active profile, and whether the VPN needs a restart.
            10. Only call tools listed in your function schema.

            The bundled mihomo core supports these proxy protocols (answer protocol-support questions
            directly from this list, never claim a protocol is unsupported if it is here):
            DIRECT, REJECT, REJECT-DROP, COMPATIBLE, PASS, PASS-RULE, REMATCH, DNS,
            RELAY (chain proxies), SELECTOR, FALLBACK, URL-TEST, LOAD-BALANCE,
            SS (Shadowsocks), SSR (ShadowsocksR), SNELL, SOCKS5, HTTP, VMESS, VLESS, TROJAN,
            HYSTERIA, HYSTERIA2, WIREGUARD, TUIC, SSH, MIERU, ANYTLS, SUDOKU, MASQUE,
            TRUST-TUNNEL, SHADOW-QUIC, OPENVPN, TAILSCALE, GOST-RELAY.
            Groups are not wire protocols and always work: selector, url-test, fallback, load-balance, relay.
            Rule providers and proxy providers are supported. If the user asks about a protocol not in this
            list, say the bundled core does not ship it and name the closest supported alternative.

            Protocol detail notes (answer accurately instead of guessing from training data):
            - SNELL: accepts version 1..5 in config. For version 5 the core connects as a v4 client
              (v5 servers are backward-compatible with v4 clients), so a v5 node works with either
              version: 4 or version: 5. There is no dedicated v5 handshake.
        """.trimIndent()
    }
}
