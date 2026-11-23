package universe.agentic.device.clash

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

object McpProtocol {
    const val PATH = "/mcp"
    const val DEFAULT_PORT = 19090
    const val PROTOCOL = "2025-06-18"

    private val acceptedVersions = setOf(PROTOCOL, "2024-11-05")

    data class Reply(val status: Int, val body: String?)

    data class Tool(
        val name: String,
        val description: String,
        val properties: List<Property> = emptyList(),
        val required: List<String> = emptyList(),
    )

    data class Property(val name: String, val description: String)

    val tools = listOf(
        Tool("status", "Whether the tunnel is running, plus its mode and traffic."),
        Tool("list_proxies", "Proxy groups and the node selected in each group."),
        Tool(
            "select_proxy",
            "Select a node in a group.",
            properties = listOf(
                Property("group", "Proxy group name"),
                Property("name", "Node name"),
            ),
            required = listOf("group", "name"),
        ),
        Tool("list_profiles", "Saved profiles. Subscription URLs are not included."),
        Tool(
            "set_profile",
            "Make a saved profile the active one.",
            properties = listOf(Property("uuid", "Profile UUID")),
            required = listOf("uuid"),
        ),
        Tool("reload", "Reload the running tunnel."),
        Tool("start", "Start the tunnel. VPN consent must already have been granted in the app."),
        Tool("stop", "Stop the tunnel."),
    )

    fun handle(body: String, invoke: (String, JsonObject) -> String): Reply {
        val root = try {
            Json.parseToJsonElement(body)
        } catch (_: Exception) {
            return Reply(400, rpcError(null, -32700, "parse error"))
        }
        if (root !is JsonObject) return Reply(400, rpcError(null, -32600, "invalid request"))
        val id = root["id"]
        val method = (root["method"] as? JsonPrimitive)?.contentOrNull
            ?: return Reply(200, rpcError(id, -32600, "invalid request"))
        val params = root["params"] as? JsonObject ?: JsonObject(emptyMap())
        if (id == null || id is JsonNull) return Reply(202, null)
        return try {
            val payload = when (method) {
                "initialize" -> initialize(params)
                "ping" -> JsonObject(emptyMap())
                "tools/list" -> buildJsonObject { put("tools", toolSchemas()) }
                "tools/call" -> callTool(params, invoke)
                    ?: return Reply(200, rpcError(id, -32602, "unknown tool"))
                else -> return Reply(200, rpcError(id, -32601, "method not found"))
            }
            Reply(200, rpcResult(id, payload))
        } catch (err: Exception) {
            Reply(200, rpcError(id, -32603, err.message ?: "internal error"))
        }
    }

    private fun initialize(params: JsonObject): JsonObject {
        val requested = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
        val version = if (requested != null && requested in acceptedVersions) requested else PROTOCOL
        return buildJsonObject {
            put("protocolVersion", version)
            put("capabilities", buildJsonObject {
                put("tools", buildJsonObject { put("listChanged", false) })
            })
            put("serverInfo", buildJsonObject {
                put("name", "AgenticClash")
                put("version", "1")
            })
        }
    }

    private fun callTool(params: JsonObject, invoke: (String, JsonObject) -> String): JsonObject? {
        val name = (params["name"] as? JsonPrimitive)?.contentOrNull
            ?: throw IllegalArgumentException("missing tool name")
        if (tools.none { it.name == name }) return null
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        return try {
            toolResult(invoke(name, args), error = false)
        } catch (err: Exception) {
            toolResult(err.message ?: "tool failed", error = true)
        }
    }

    private fun toolSchemas(): JsonArray = buildJsonArray {
        tools.forEach { tool ->
            add(buildJsonObject {
                put("name", tool.name)
                put("description", tool.description)
                put("inputSchema", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        tool.properties.forEach { property ->
                            put(property.name, buildJsonObject {
                                put("type", "string")
                                put("description", property.description)
                            })
                        }
                    })
                    put("required", buildJsonArray { tool.required.forEach { add(JsonPrimitive(it)) } })
                    put("additionalProperties", false)
                })
            })
        }
    }

    private fun toolResult(text: String, error: Boolean): JsonObject = buildJsonObject {
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", text)
            })
        })
        put("isError", error)
    }

    private fun rpcResult(id: JsonElement?, result: JsonObject): String {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            put("result", result)
        }.toString()
    }

    private fun rpcError(id: JsonElement?, code: Int, message: String): String {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null && id !is JsonNull) put("id", id)
            put("error", buildJsonObject {
                put("code", code)
                put("message", message)
            })
        }.toString()
    }
}
