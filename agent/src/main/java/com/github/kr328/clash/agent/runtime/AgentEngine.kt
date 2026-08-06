package com.github.kr328.clash.agent.runtime

import com.github.kr328.clash.agent.authorization.AgentAuthorizationDecision
import com.github.kr328.clash.agent.authorization.AgentAuthorizationPolicy
import com.github.kr328.clash.agent.model.AgentConversationMessage
import com.github.kr328.clash.agent.model.AgentMessageRole
import com.github.kr328.clash.agent.model.AgentProviderSettings
import com.github.kr328.clash.agent.model.AgentRunEvent
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
    suspend fun run(
        settings: AgentProviderSettings,
        history: List<AgentConversationMessage>,
        prompt: String,
        executor: AgentToolExecutor,
        approvalHandler: AgentApprovalHandler,
        emit: suspend (AgentRunEvent) -> Unit,
    ): String {
        require(settings.isConfigured) { "请先配置模型接口、API Key 和模型名称" }
        require(prompt.isNotBlank()) { "消息不能为空" }

        val messages = mutableListOf<JsonObject>()
        messages += buildJsonObject {
            put("role", "system")
            put("content", SYSTEM_PROMPT)
        }
        history.takeLast(MAX_CONTEXT_MESSAGES).forEach { message ->
            if (message.role == AgentMessageRole.USER || message.role == AgentMessageRole.ASSISTANT) {
                messages += buildJsonObject {
                    put("role", message.role.name.lowercase())
                    put("content", message.content)
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
        private const val MAX_CONTEXT_MESSAGES = 24
        private const val MAX_TOOL_RESULT_CHARS = 120_000
        private const val MAX_REQUEST_ATTEMPTS = 3

        private val SYSTEM_PROMPT = """
            You are the built-in configuration and operations agent for Clash Meta for Android, powered by mihomo.
            Reply in the user's language. Be concise, concrete, and transparent about every change.

            You can create a complete configuration from zero, maintain existing configurations, inspect installed apps,
            manage Android per-app access control and VPN integration settings, operate the VPN, switch selectors, refresh
            providers, inspect saved logs, and inspect or close connections. The complete YAML is
            the source of truth: editing that YAML lets you manage proxies, proxy-providers, proxy-groups, rules,
            rule-providers, DNS, TUN, listeners, hosts, sniffer, NTP, geodata, routing and every other mihomo field.

            Configuration safety rules:
            1. Before modifying an existing profile, always call profile_read_config and use its expected_sha256.
            2. Return a complete replacement YAML, preserving every unrelated or unknown field and all comments when possible.
            3. Never invent server addresses, ports, UUIDs, passwords, keys, subscription URLs, or provider URLs.
            4. Never print credentials or full proxy URIs in conversational text. Secrets may only travel inside tool arguments.
            5. Ensure every rule target and group member exists. Keep MATCH last. Avoid DNS leaks and routing loops.
            6. For a new empty configuration with no supplied nodes, create a useful DIRECT/REJECT baseline and explain that
               proxy nodes still need to be supplied. Use installed_apps when the request depends on apps on this device.
            7. profile_create and profile_replace_config validate through the bundled mihomo core and automatically roll back
               on failure. If validation fails, diagnose the exact error, correct the YAML, and retry only when safe.
            8. After tools finish, summarize what changed, validation status, active profile, and whether VPN restart is needed.
            9. Do not claim an operation succeeded until its tool result says success.
            10. Do not call nonexistent tools or ask the user to manually edit files that an exposed tool can handle.
            11. App-aware routing has two layers: use YAML rules for policy selection and access_control_replace only when the
                user wants Android to include/exclude entire apps from the VPN. Call installed_apps first and use exact packages.
             12. Before changing app overrides or Android VPN settings, read their current complete state and preserve fields the
                 user did not ask to change. Prefer runtime_set_mode for a temporary mode switch.
             13. Before any modification of a profile, override, or DNS/TUN setting, call runtime.status and note core_version.
                 Only write fields supported by that mihomo core version: never emit YAML options the running core does not
                 support. If the user asks for a feature that depends on a newer core, say so and propose the closest supported
                 alternative instead of writing an invalid field.
        """.trimIndent()
    }
}
