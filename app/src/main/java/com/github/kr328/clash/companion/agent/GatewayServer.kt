package com.github.kr328.clash.companion.agent

import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.companion.CompanionPin
import com.github.kr328.clash.companion.CompanionStore
import com.github.kr328.clash.companion.protocol.CanonicalJson
import com.github.kr328.clash.companion.protocol.Ids
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.net.ssl.SSLServerSocketFactory

/**
 * The clashctl gateway (PROTOCOL.md §8) — the only thing exposed on the LAN. Serves the P1
 * app-only endpoints over pinned HTTPS, enforcing bearer auth (§5.2) before any hook and a strict
 * endpoint whitelist (§2). Everything is canonical JSON (§3.4); errors use the uniform envelope
 * (§5.4, §5.5).
 *
 * @param port 0 binds an ephemeral port; read [getListeningPort] after [start] for the actual one.
 */
class GatewayServer(
    port: Int,
    sslFactory: SSLServerSocketFactory,
    private val pairingStore: PairingStore,
    private val store: CompanionStore,
    private val hooks: CompanionHooks,
) : NanoHTTPD(port) {

    init {
        makeSecure(sslFactory, null)
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            handle(session)
        } catch (t: Throwable) {
            Log.w("Companion gateway error: ${t.message}")
            error(Response.Status.INTERNAL_ERROR, "internal", "unexpected agent error")
        }
    }

    private fun handle(session: IHTTPSession): Response {
        // PIN-assisted pairing (§6.4) is the ONE endpoint without bearer auth — it issues the token.
        // Gated instead by the on-screen PIN (single-use, expiring, rate-limited).
        if (session.method == Method.POST && session.uri == "/v1/pair") return pair(session)

        // Bearer auth (§5.2) — applies to every other endpoint, before any hook or routing.
        val header = session.headers["authorization"].orEmpty()
        val token = if (header.startsWith(BEARER)) header.substring(BEARER.length) else null
        val pairing = token?.let { pairingStore.authenticate(it) }
            ?: return error(Response.Status.UNAUTHORIZED, "unauthorized", "missing or invalid token")

        // Best-effort: record the controller's advertised identity for the management UI.
        pairingStore.recordController(
            pairing.tokenHash,
            session.headers["x-clashctl-id"],
            session.headers["x-clashctl-name"],
        )

        val uri = session.uri
        return when {
            session.method == Method.GET && uri == "/v1/status" -> status()
            session.method == Method.POST && uri == "/v1/power" -> power(session)
            session.method == Method.POST && uri == "/v1/subscription" -> subscription(session)
            session.method == Method.POST && uri == "/v1/rename" -> rename(session)
            // Explicitly refuse core forwarding / upgrade in P1 (§2, §9 is P2-gated).
            uri.startsWith("/v1/core") || uri == "/upgrade" ->
                error(Response.Status.FORBIDDEN, "forbidden", "endpoint not permitted")
            else -> error(Response.Status.NOT_FOUND, "not_found", "unknown endpoint")
        }
    }

    private fun status(): Response = ok(
        buildJsonObject {
            put("app", CompanionStore.APP_ID)
            putJsonArray("capabilities") {
                add("status"); add("power"); add("subscription"); add("rename")
            }
            put("id", store.deviceId)
            put("name", store.displayName)
            put("power", hooks.powerState())
            put("ver", PROTOCOL_VERSION)
        },
    )

    private fun power(session: IHTTPSession): Response {
        val body = parseBody(session) ?: return badRequest("malformed body")
        val action = body["action"]?.jsonPrimitive?.content
        if (action !in setOf("on", "off", "toggle")) return badRequest("invalid action")
        return try {
            val resulting = hooks.power(action!!)
            ok(buildJsonObject { put("ok", true); put("power", resulting) })
        } catch (e: CompanionHooks.PowerUnavailable) {
            error(Response.Status.INTERNAL_ERROR, "internal", e.message ?: "power unavailable")
        }
    }

    private fun subscription(session: IHTTPSession): Response {
        val body = parseBody(session) ?: return badRequest("malformed body")
        val url = body["url"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
        val payload = body["payload"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
        val name = body["name"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_PROFILE_NAME
        // Exactly one of url/payload (§8.4).
        if ((url == null) == (payload == null)) return badRequest("exactly one of url/payload required")
        return try {
            hooks.importSubscription(url, payload, name)
            ok(buildJsonObject { put("ok", true) })
        } catch (e: CompanionHooks.PayloadUnsupported) {
            badRequest(e.message ?: "inline payload not supported")
        }
    }

    private fun rename(session: IHTTPSession): Response {
        val body = parseBody(session) ?: return badRequest("malformed body")
        val name = body["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return badRequest("empty name")
        hooks.rename(name)
        return ok(buildJsonObject { put("name", name); put("ok", true) })
    }

    /**
     * PIN-assisted pairing (§6.4). Body: `{"pin":"<code>","device":{"id":"<id>","name":"<name>"}}`.
     * On a correct PIN, issues and returns a bearer token bound to the controller; wrong PIN →
     * `pin_invalid` (403), too many attempts → `pin_rate_limited` (429).
     */
    private fun pair(session: IHTTPSession): Response {
        val body = parseBody(session) ?: return badRequest("malformed body")
        val pin = body["pin"]?.jsonPrimitive?.content ?: return badRequest("pin required")
        val device = body["device"]?.jsonObject
        val controllerId = device?.get("id")?.jsonPrimitive?.content
        val controllerName = device?.get("name")?.jsonPrimitive?.content

        return when (CompanionPin.verify(pin)) {
            CompanionPin.Result.OK -> {
                val token = pairingStore.issueToken()
                pairingStore.recordController(Ids.tokenHash(token), controllerId, controllerName)
                ok(buildJsonObject { put("ok", true); put("token", token) })
            }
            CompanionPin.Result.RATE_LIMITED ->
                error(429, "pin_rate_limited", "too many attempts")
            CompanionPin.Result.WRONG, CompanionPin.Result.EXPIRED ->
                error(Response.Status.FORBIDDEN, "pin_invalid", "wrong or expired pin")
        }
    }

    // --- helpers ------------------------------------------------------------------------------

    private fun parseBody(session: IHTTPSession): JsonObject? = try {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val data = files["postData"] ?: session.queryParameterString ?: return null
        Json.parseToJsonElement(data).jsonObject
    } catch (_: Exception) {
        null
    }

    private fun ok(obj: JsonObject): Response =
        newFixedLengthResponse(Response.Status.OK, MIME_JSON, CanonicalJson.encode(obj))

    private fun badRequest(message: String): Response =
        error(Response.Status.BAD_REQUEST, "bad_request", message)

    private fun error(status: Response.Status, code: String, message: String): Response =
        newFixedLengthResponse(status, MIME_JSON, errorBody(code, message))

    /** For HTTP codes NanoHTTPD's [Response.Status] enum doesn't cover (e.g. 429). */
    private fun error(httpCode: Int, code: String, message: String): Response {
        val status = object : Response.IStatus {
            override fun getRequestStatus(): Int = httpCode
            override fun getDescription(): String = "$httpCode $code"
        }
        return newFixedLengthResponse(status, MIME_JSON, errorBody(code, message))
    }

    private fun errorBody(code: String, message: String): String =
        CanonicalJson.encode(
            buildJsonObject {
                putJsonObject("error") {
                    put("code", code)
                    put("message", message)
                }
            },
        )

    private companion object {
        const val BEARER = "Bearer "
        const val MIME_JSON = "application/json"
        const val PROTOCOL_VERSION = 1
        const val DEFAULT_PROFILE_NAME = "Shared"
    }
}
