package com.github.kr328.clash.agent.protocol

import com.github.kr328.clash.agent.model.AgentProviderSettings
import com.github.kr328.clash.agent.model.AgentApiFormat
import com.github.kr328.clash.agent.tools.AgentToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class OpenAIToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class OpenAICompletion(
    val content: String,
    val toolCalls: List<OpenAIToolCall>,
    val assistantMessage: JsonObject,
)

class OpenAICompatibleClient(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun complete(
        settings: AgentProviderSettings,
        messages: JsonArray,
        tools: List<AgentToolSpec>,
        onText: suspend (String) -> Unit,
    ): OpenAICompletion = withContext(Dispatchers.IO) {
        val connection = (URL(endpoint(settings)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            if (settings.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            }
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream, application/json")
        }

        val request = when (settings.apiFormat) {
            AgentApiFormat.CHAT_COMPLETIONS -> chatRequest(settings, messages, tools)
            AgentApiFormat.RESPONSES -> responsesRequest(settings, messages, tools)
        }

        try {
            connection.outputStream.buffered().use { output ->
                output.write(request.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("模型服务返回 HTTP $status：${extractError(error)}")
            }

            connection.inputStream.bufferedReader().use { reader ->
                when (settings.apiFormat) {
                    AgentApiFormat.CHAT_COMPLETIONS -> parseResponse(reader, onText)
                    AgentApiFormat.RESPONSES -> parseResponses(reader, onText)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Performs a minimal real chat completion so URL, credentials and model are all verified. */
    suspend fun testConnection(settings: AgentProviderSettings): String {
        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", "Connection test. Reply with OK only.")
            })
        }
        return complete(settings, messages, emptyList()) {}.content
    }

    private suspend fun parseResponse(
        reader: BufferedReader,
        onText: suspend (String) -> Unit,
    ): OpenAICompletion {
        var first: String? = null
        while (first == null) {
            val line = reader.readLine() ?: break
            if (line.isNotBlank()) first = line
        }
        var initial = first ?: throw IOException("模型服务返回了空响应")
        if (initial.startsWith("{") || initial.startsWith("[")) {
            val body = buildString {
                append(initial)
                reader.forEachLine { append('\n').append(it) }
            }
            return parseNonStreaming(body, onText)
        }
        while (!initial.startsWith("data:")) {
            initial = reader.readLine() ?: throw IOException("模型服务未返回有效的 SSE data 事件")
        }

        val content = StringBuilder()
        val calls = linkedMapOf<Int, MutableToolCall>()
        var lastEmission = 0L

        suspend fun consume(line: String) {
            coroutineContext.ensureActive()
            if (!line.startsWith("data:")) return
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty() || payload == "[DONE]") return

            val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return
            val delta = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject ?: return
            delta["content"]?.jsonPrimitive?.contentOrNull?.let(content::append)

            delta["tool_calls"]?.jsonArray?.forEach { element ->
                val call = element.jsonObject
                val index = call["index"]?.jsonPrimitive?.intOrNull ?: calls.size
                val accumulator = calls.getOrPut(index) { MutableToolCall() }
                call["id"]?.jsonPrimitive?.contentOrNull?.let { accumulator.id = it }
                call["function"]?.jsonObject?.let { function ->
                    function["name"]?.jsonPrimitive?.contentOrNull?.let(accumulator.name::append)
                    function["arguments"]?.jsonPrimitive?.contentOrNull?.let(accumulator.arguments::append)
                }
            }

            val now = System.currentTimeMillis()
            if (content.isNotEmpty() && now - lastEmission >= STREAM_FRAME_MS) {
                onText(content.toString())
                lastEmission = now
            }
        }

        consume(initial)
        while (true) {
            val line = reader.readLine() ?: break
            consume(line)
        }
        if (content.isNotEmpty()) onText(content.toString())

        return completion(content.toString(), calls.values.mapIndexed { index, call ->
            OpenAIToolCall(
                id = call.id.ifBlank { "call_$index" },
                name = call.name.toString(),
                arguments = call.arguments.toString().ifBlank { "{}" },
            )
        })
    }

    private suspend fun parseNonStreaming(body: String, onText: suspend (String) -> Unit): OpenAICompletion {
        val root = json.parseToJsonElement(body).jsonObject
        val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: throw IOException("模型响应缺少 choices.message")
        val content = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (content.isNotEmpty()) onText(content)
        val calls = message["tool_calls"]?.jsonArray?.mapIndexed { index, element ->
            val call = element.jsonObject
            val function = call["function"]?.jsonObject ?: JsonObject(emptyMap())
            OpenAIToolCall(
                call["id"]?.jsonPrimitive?.contentOrNull ?: "call_$index",
                function["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}",
            )
        }.orEmpty()
        return completion(content, calls)
    }

    /**
     * Parses Responses API SSE stream. Events:
     *  - response.output_text.delta : text deltas
     *  - response.output_item.done  : completed function call (call_id + name + arguments)
     *  - response.completed         : terminal
     */
    private suspend fun parseResponses(
        reader: BufferedReader,
        onText: suspend (String) -> Unit,
    ): OpenAICompletion {
        val content = StringBuilder()
        val calls = linkedMapOf<String, MutableResponsesCall>()
        var lastEmission = 0L

        suspend fun consume(line: String) {
            coroutineContext.ensureActive()
            if (!line.startsWith("data:")) return
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty() || payload == "[DONE]") return

            val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return
            when (root["type"]?.jsonPrimitive?.contentOrNull) {
                "response.output_text.delta" -> {
                    root["delta"]?.jsonPrimitive?.contentOrNull?.let(content::append)
                }
                "response.output_item.done" -> {
                    val item = root["item"]?.jsonObject ?: return
                    if (item["type"]?.jsonPrimitive?.contentOrNull == "function_call") {
                        val id = item["call_id"]?.jsonPrimitive?.contentOrNull
                            ?: item["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val arguments = item["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (name.isNotEmpty()) {
                            val acc = calls.getOrPut(if (id.isBlank()) name else id) { MutableResponsesCall() }
                            if (acc.id.isBlank()) acc.id = id
                            if (acc.name.isBlank()) acc.name.append(name)
                            if (arguments.isNotEmpty()) acc.arguments.append(arguments)
                        }
                    }
                }
                "response.function_call_arguments.done" -> {
                    val callId = root["call_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    root["arguments"]?.jsonPrimitive?.contentOrNull?.let { args ->
                        val acc = calls.getOrPut(if (callId.isBlank()) "call_${calls.size}" else callId) { MutableResponsesCall() }
                        if (acc.id.isBlank()) acc.id = callId
                        if (acc.arguments.isBlank()) acc.arguments.append(args)
                    }
                }
                else -> Unit
            }

            val now = System.currentTimeMillis()
            if (content.isNotEmpty() && now - lastEmission >= STREAM_FRAME_MS) {
                onText(content.toString())
                lastEmission = now
            }
        }

        var line = reader.readLine()
        while (line != null) {
            consume(line)
            line = reader.readLine()
        }
        if (content.isNotEmpty()) onText(content.toString())

        return completion(content.toString(), calls.values.mapIndexed { index, call ->
            OpenAIToolCall(
                id = call.id.ifBlank { "call_$index" },
                name = call.name.toString(),
                arguments = call.arguments.toString().ifBlank { "{}" },
            )
        })
    }

    private fun completion(content: String, calls: List<OpenAIToolCall>): OpenAICompletion {
        val assistant = buildJsonObject {
            put("role", "assistant")
            put("content", if (content.isEmpty()) JsonNull else JsonPrimitive(content))
            if (calls.isNotEmpty()) {
                put("tool_calls", buildJsonArray {
                    calls.forEach { call ->
                        add(buildJsonObject {
                            put("id", call.id)
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", call.name)
                                put("arguments", call.arguments)
                            })
                        })
                    }
                })
            }
        }
        return OpenAICompletion(content, calls, assistant)
    }

    private fun endpoint(settings: AgentProviderSettings): String {
        val baseUrl = settings.baseUrl
        val normalized = baseUrl.trim().trimEnd('/')
        return when (settings.apiFormat) {
            AgentApiFormat.CHAT_COMPLETIONS ->
                if (normalized.endsWith("/chat/completions")) normalized else "$normalized/chat/completions"
            AgentApiFormat.RESPONSES ->
                if (normalized.endsWith("/responses")) normalized else "$normalized/responses"
        }
    }

    private fun chatRequest(
        settings: AgentProviderSettings,
        messages: JsonArray,
        tools: List<AgentToolSpec>,
    ): JsonObject = buildJsonObject {
        put("model", settings.model)
        put("messages", messages)
        put("stream", true)
        if (tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                tools.forEach { tool ->
                    add(buildJsonObject {
                        put("type", "function")
                        put("function", buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parameters)
                        })
                    })
                }
            })
            put("tool_choice", "auto")
        }
    }

    private fun responsesRequest(
        settings: AgentProviderSettings,
        messages: JsonArray,
        tools: List<AgentToolSpec>,
    ): JsonObject = buildJsonObject {
        put("model", settings.model)
        put("stream", true)

        // System prompts move to the top-level instructions field.
        val instructions = buildList {
            messages.forEach { message ->
                if (message.jsonObject["role"]?.jsonPrimitive?.contentOrNull == "system") {
                    message.jsonObject["content"]?.jsonPrimitive?.contentOrNull?.let(::add)
                }
            }
        }.joinToString("\n\n")
        if (instructions.isNotBlank()) put("instructions", instructions)

        // Convert the Chat-style message list into Responses API input items.
        put("input", buildJsonArray {
            messages.forEach { message ->
                val role = message.jsonObject["role"]?.jsonPrimitive?.contentOrNull
                val content = message.jsonObject["content"]?.jsonPrimitive?.contentOrNull
                when (role) {
                    "system" -> Unit
                    "user" -> add(buildJsonObject {
                        put("role", "user")
                        put("content", content ?: "")
                    })
                    "assistant" -> {
                        val toolCalls = message.jsonObject["tool_calls"]?.jsonArray
                        if (toolCalls != null && toolCalls.isNotEmpty()) {
                            toolCalls.forEach { call ->
                                val fn = call.jsonObject["function"]?.jsonObject ?: JsonObject(emptyMap())
                                add(buildJsonObject {
                                    put("type", "function_call")
                                    put("call_id", call.jsonObject["id"]?.jsonPrimitive?.contentOrNull.orEmpty())
                                    put("name", fn["name"]?.jsonPrimitive?.contentOrNull.orEmpty())
                                    put("arguments", fn["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty())
                                })
                            }
                        } else {
                            add(buildJsonObject {
                                put("role", "assistant")
                                put("content", content ?: "")
                            })
                        }
                    }
                    "tool" -> add(buildJsonObject {
                        put("type", "function_call_output")
                        put("call_id", message.jsonObject["tool_call_id"]?.jsonPrimitive?.contentOrNull.orEmpty())
                        put("output", content ?: "")
                    })
                }
            }
        })

        if (tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                tools.forEach { tool ->
                    add(buildJsonObject {
                        put("type", "function")
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.parameters)
                    })
                }
            })
        }
    }

    private fun extractError(body: String): String = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.take(1000) ?: body.take(1000).ifBlank { "无错误详情" }

    private class MutableToolCall {
        var id: String = ""
        val name = StringBuilder()
        val arguments = StringBuilder()
    }

    private class MutableResponsesCall {
        var id: String = ""
        val name = StringBuilder()
        val arguments = StringBuilder()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 180_000
        // Keep the source buffer fresh; visual pacing is handled independently on Android's VSync.
        private const val STREAM_FRAME_MS = 24L
    }
}
