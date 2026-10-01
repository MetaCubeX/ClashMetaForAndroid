package com.github.kr328.clash.service.webdav

import android.util.Base64
import com.github.kr328.clash.common.log.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class WebdavConfig(
    val url: String,
    val username: String,
    val password: String,
    val remotePath: String,
    val clientId: String,
)

interface HttpTransport {
    fun execute(request: HttpRequest): HttpResponse
}

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String? = null,
)

data class HttpResponse(
    val status: Int,
    val statusText: String,
    val body: String,
    val headers: Map<String, String>,
) {
    val ok: Boolean get() = status in 200..299

    fun header(name: String): String? {
        val want = name.lowercase()
        return headers.entries.firstOrNull { it.key.lowercase() == want }?.value
    }
}

class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build(),
) : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse {
        val builder = Request.Builder().url(request.url)
        for ((key, value) in request.headers) builder.header(key, value)
        val body = when (request.method) {
            "GET", "DELETE", "UNLOCK", "MOVE" -> null
            "MKCOL" -> ByteArray(0).toRequestBody(null)
            "PROPFIND" -> (request.body ?: "").toRequestBody("application/xml; charset=utf-8".toMediaType())
            else -> request.body?.toRequestBody("application/json; charset=utf-8".toMediaType())
        }
        builder.method(request.method, body)
        client.newCall(builder.build()).execute().use { response ->
            val headerMap = linkedMapOf<String, String>()
            for (name in response.headers.names()) {
                headerMap[name] = response.header(name).orEmpty()
            }
            return HttpResponse(
                status = response.code,
                statusText = response.message,
                body = response.body?.string().orEmpty(),
                headers = headerMap,
            )
        }
    }
}

class WebdavClient(
    private val transport: HttpTransport = OkHttpTransport(),
) {
    fun get(config: WebdavConfig, fileName: String): WebdavGetResult {
        val response = request("GET", fileUrl(config.url, config.remotePath, fileName), config)
        if (response.status == 404) return WebdavGetResult(found = false, body = "", etag = null)
        if (!response.ok) throw WebdavException(statusCode(response, "get"), response.status, statusMessage(response, "get"))
        return WebdavGetResult(found = true, body = response.body, etag = response.header("etag"))
    }

    fun list(config: WebdavConfig, subdir: String = ""): List<WebdavListEntry> {
        val url = if (subdir.isBlank()) {
            folderUrl(config.url, config.remotePath)
        } else {
            val nested = fileUrl(config.url, config.remotePath, subdir.trim('/'))
            if (nested.endsWith("/")) nested else "$nested/"
        }
        val response = request(
            "PROPFIND",
            url,
            config,
            body = """<?xml version="1.0" encoding="utf-8"?><D:propfind xmlns:D="DAV:"><D:prop><D:getetag/><D:getlastmodified/><D:resourcetype/></D:prop></D:propfind>""",
            extra = mapOf("Depth" to "1", "Content-Type" to "application/xml; charset=utf-8"),
        )
        if (response.status == 404) return emptyList()
        if (response.status != 207 && !response.ok) {
            throw WebdavException(statusCode(response, "propfind"), response.status, statusMessage(response, "propfind"))
        }
        return parsePropfindEntries(response.body, url)
    }

    fun put(
        config: WebdavConfig,
        fileName: String,
        body: String,
        ifMatch: String? = null,
        createOnly: Boolean = false,
    ): WebdavPutResult {
        val tempName = webdavTempFileName(fileName, config.clientId)
        putDirect(config, tempName, body)
        val destUrl = fileUrl(config.url, config.remotePath, fileName)
        val extra = linkedMapOf(
            "Destination" to destUrl,
            "Overwrite" to if (createOnly) "F" else "T",
        )
        if (!ifMatch.isNullOrEmpty()) {
            extra["If-Match"] = ifMatch
            extra["If"] = "<$destUrl> ($ifMatch)"
        }
        val moved = tryMove(config, tempName, extra)
        if (moved != null) return moved
        val written = putDirect(config, fileName, body, ifMatch, createOnly)
        try {
            delete(config, tempName)
        } catch (_: Exception) {
        }
        return written
    }

    fun delete(config: WebdavConfig, fileName: String) {
        val response = request("DELETE", fileUrl(config.url, config.remotePath, fileName), config)
        if (response.status == 404 || response.status == 204 || response.ok) return
        throw WebdavException(statusCode(response, "delete"), response.status, statusMessage(response, "delete"))
    }

    fun ensureParents(config: WebdavConfig) {
        val target = fileUrl(config.url, config.remotePath, "$WEBDAV_PROFILES_DIR/_placeholder.json")
        val root = if (config.url.trim().endsWith("/")) config.url.trim() else "${config.url.trim()}/"
        for (collection in collectionUrlsBetween(root, target)) {
            val colUrl = if (collection.endsWith("/")) collection else "$collection/"
            val response = request("MKCOL", colUrl, config)
            if (response.status == 201 || response.status == 405 || response.status == 409 || response.status == 301 || response.ok) {
                continue
            }
            throw WebdavException(statusCode(response, "mkcol"), response.status, statusMessage(response, "mkcol"))
        }
    }

    private fun putDirect(
        config: WebdavConfig,
        fileName: String,
        body: String,
        ifMatch: String? = null,
        createOnly: Boolean = false,
        unlockTried: Boolean = false,
    ): WebdavPutResult {
        val url = fileUrl(config.url, config.remotePath, fileName)
        val extra = linkedMapOf("Content-Type" to "application/json; charset=utf-8")
        if (!ifMatch.isNullOrEmpty()) extra["If-Match"] = ifMatch
        else if (createOnly) extra["If-None-Match"] = "*"
        val response = request("PUT", url, config, body, extra)
        if (response.status == 412) throw WebdavConflictException()
        if (response.status == 423 && !unlockTried) {
            val tokens = (extractLockTokens(response.body) + discoverLockTokens(config, fileName)).distinct()
            for (token in tokens) unlock(config, fileName, token)
            return putDirect(config, fileName, body, ifMatch, createOnly, unlockTried = true)
        }
        if (response.status == 201 || response.status == 204 || response.ok) {
            return WebdavPutResult(etag = response.header("etag"))
        }
        throw WebdavException(statusCode(response, "put"), response.status, statusMessage(response, "put"))
    }

    private fun tryMove(
        config: WebdavConfig,
        tempName: String,
        extra: Map<String, String>,
        unlockTried: Boolean = false,
    ): WebdavPutResult? {
        val response = request("MOVE", fileUrl(config.url, config.remotePath, tempName), config, extra = extra)
        if (response.status == 405 || response.status == 501) return null
        if (response.status == 412) {
            try {
                delete(config, tempName)
            } catch (_: Exception) {
            }
            throw WebdavConflictException()
        }
        if (response.status == 423 && !unlockTried) {
            val tokens = (extractLockTokens(response.body) + discoverLockTokens(config, tempName)).distinct()
            for (token in tokens) unlock(config, tempName, token)
            return tryMove(config, tempName, extra, unlockTried = true)
        }
        if (response.status == 201 || response.status == 204 || response.ok) {
            return WebdavPutResult(etag = response.header("etag"))
        }
        throw WebdavException(statusCode(response, "move"), response.status, statusMessage(response, "move"))
    }

    private fun discoverLockTokens(config: WebdavConfig, fileName: String): List<String> {
        return try {
            val response = request(
                "PROPFIND",
                fileUrl(config.url, config.remotePath, fileName),
                config,
                body = """<?xml version="1.0" encoding="utf-8"?><D:propfind xmlns:D="DAV:"><D:prop><D:lockdiscovery/></D:prop></D:propfind>""",
                extra = mapOf("Depth" to "0", "Content-Type" to "application/xml; charset=utf-8"),
            )
            if (response.status != 207 && !response.ok) emptyList() else extractLockTokens(response.body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun unlock(config: WebdavConfig, fileName: String, token: String) {
        try {
            request(
                "UNLOCK",
                fileUrl(config.url, config.remotePath, fileName),
                config,
                extra = mapOf("Lock-Token" to "<${token.trim('<', '>')}>"),
            )
        } catch (_: Exception) {
        }
    }

    private fun request(
        method: String,
        url: String,
        config: WebdavConfig,
        body: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): HttpResponse {
        if (config.username.trim().isEmpty()) throw WebdavException("empty_user")
        if (config.password.isEmpty()) throw WebdavException("empty_password")
        val auth = Base64.encodeToString(
            "${config.username}:${config.password}".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )
        val headers = linkedMapOf(
            "Authorization" to "Basic $auth",
            "User-Agent" to WEBDAV_USER_AGENT,
        )
        headers.putAll(extra)
        return try {
            val response = transport.execute(HttpRequest(method, url, headers, body))
            Log.i("WebDAV $method ${response.status}")
            if (response.status in 300..399) throw WebdavException("redirect", response.status)
            response
        } catch (err: WebdavException) {
            throw err
        } catch (err: Exception) {
            val message = err.message.orEmpty()
            if (message.contains("abort", true) || message.contains("timeout", true)) {
                throw WebdavException("timeout")
            }
            throw WebdavException("connect", detail = message)
        }
    }

    private fun statusCode(response: HttpResponse, context: String): String {
        if (response.status == 401 || response.status == 403) return "auth"
        if (response.status == 423) return "locked"
        if (response.status == 404 && (context == "put" || context == "mkcol")) return "not_found"
        if (response.status == 409 && (context == "put" || context == "mkcol")) return "conflict_parent"
        if (response.status == 507) return "no_space"
        return "http"
    }

    private fun statusMessage(response: HttpResponse, context: String): String {
        val snippet = response.body.replace(Regex("\\s+"), " ").trim().take(180)
        return listOf(context, response.status.toString(), response.statusText, snippet)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}
