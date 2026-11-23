package universe.agentic.device.clash

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpProtocolTest {
    @Test
    fun initializeEchoesAcceptedVersion() {
        val reply = McpProtocol.handle(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05"}}""",
        ) { _, _ -> "{}" }
        val root = Json.parseToJsonElement(reply.body!!).jsonObject
        assertEquals(200, reply.status)
        assertEquals("2024-11-05", root["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
    }

    @Test
    fun unknownToolIsInvalidParams() {
        val reply = McpProtocol.handle(
            """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"missing"}}""",
        ) { _, _ -> "{}" }
        val error = Json.parseToJsonElement(reply.body!!).jsonObject["error"]!!.jsonObject
        assertEquals(-32602, error["code"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun toolFailureStaysAResult() {
        val reply = McpProtocol.handle(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"status"}}""",
        ) { _, _ -> throw IllegalStateException("tunnel is not running") }
        val result = Json.parseToJsonElement(reply.body!!).jsonObject["result"]!!.jsonObject
        assertEquals(true, result["isError"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(result["content"].toString().contains("tunnel is not running"))
    }

    @Test
    fun notificationHasNoBody() {
        val reply = McpProtocol.handle("""{"jsonrpc":"2.0","method":"notifications/initialized"}""") { _, _ -> "{}" }
        assertEquals(202, reply.status)
        assertNull(reply.body)
    }

    @Test
    fun parseErrorUses400() {
        val reply = McpProtocol.handle("{") { _, _ -> "{}" }
        assertEquals(400, reply.status)
        val error = Json.parseToJsonElement(reply.body!!).jsonObject["error"]!!.jsonObject
        assertEquals(-32700, error["code"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun blankTokenNeverMatches() {
        assertFalse(McpHttpServer.bearerMatches("Bearer secret", ""))
        assertFalse(McpHttpServer.bearerMatches(null, "secret"))
        assertTrue(McpHttpServer.bearerMatches("Bearer secret", "secret"))
    }
}
