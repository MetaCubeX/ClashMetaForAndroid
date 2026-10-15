package com.github.kr328.clash.service.webdav

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

const val WEBDAV_SYNC_KIND = "clash-meta-android-webdav-sync"
const val WEBDAV_PROFILE_KIND = "clash-meta-profile"
const val WEBDAV_SYNC_VERSION = 1
const val WEBDAV_META_FILE = "_sync.json"
const val WEBDAV_PROFILES_DIR = "profiles"
const val WEBDAV_DEFAULT_URL = "https://alist.taokdao.com/dav"
const val WEBDAV_DEFAULT_LAN_URL = "http://192.168.1.69:5244/dav"
const val WEBDAV_LAN_PROBE_MS = 2_500L
const val WEBDAV_DEFAULT_USERNAME = "clarence"
const val WEBDAV_DEFAULT_REMOTE_PATH = "/sync/Configs/clash-meta"
const val WEBDAV_LEGACY_MISREAD_URL = "https://alist.taokodao.com/dav"
const val WEBDAV_LEGACY_REMOTE_PATH = "/clash-meta"
const val WEBDAV_USER_AGENT = "ClashMetaForAndroid/1.0"
const val WEBDAV_TMP_SUFFIX = ".tmp"
const val WEBDAV_TMP_MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000
const val WEBDAV_TOMBSTONE_TTL_MS = 60L * 24 * 60 * 60 * 1000
const val WEBDAV_MAX_FILE_BYTES = 8 * 1024 * 1024
const val WEBDAV_MAX_PROFILE_BYTES = 32 * 1024 * 1024

open class WebdavException(
    val code: String,
    val status: Int? = null,
    val detail: String = "",
) : Exception(if (detail.isBlank()) code else detail)

class WebdavConflictException : WebdavException("conflict", 412)

data class WebdavGetResult(
    val found: Boolean,
    val body: String,
    val etag: String?,
)

data class WebdavPutResult(
    val etag: String?,
)

data class WebdavListEntry(
    val href: String,
    val name: String,
    val etag: String?,
    val collection: Boolean,
    val lastModified: String? = null,
)

fun normalizeWebdavUrl(raw: String): String {
    val url = raw.trim().trimEnd('/')
    if (url.isEmpty() || url == WEBDAV_LEGACY_MISREAD_URL) return WEBDAV_DEFAULT_URL
    return url
}

/** Blank stays blank so a cleared LAN address stays off. The first-run default is applied by [StoredWebdavSettings]. */
fun normalizeWebdavLanUrl(raw: String?): String {
    return raw?.trim()?.trimEnd('/').orEmpty()
}

/**
 * A LAN probe does not send credentials. A normal web server that answers 200 or 404
 * is not selected, so the later authenticated request is not sent there.
 */
fun isWebdavEndpointResponse(response: HttpResponse): Boolean {
    if (response.header("DAV")?.isNotBlank() == true) return true
    if (response.status == 207) return true
    if (response.status == 401 || response.status == 403) {
        return response.header("WWW-Authenticate")?.isNotBlank() == true
    }
    return false
}

fun isPrivateWebdavHost(hostname: String): Boolean {
    val bare = hostname.trim().lowercase().removeSuffix(".").removePrefix("[").removeSuffix("]")
    if (bare.isEmpty()) return false
    if (
        bare == "localhost" ||
        bare.endsWith(".localhost") ||
        bare.endsWith(".local") ||
        bare.endsWith(".lan") ||
        bare.endsWith(".home")
    ) {
        return true
    }
    if (bare == "::1") return true
    if (!bare.contains('.') && !bare.contains(':')) return true
    val v4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(bare)
    if (v4 != null) {
        val parts = v4.groupValues.drop(1).map { it.toInt() }
        if (parts.any { it > 255 }) return false
        val a = parts[0]
        val b = parts[1]
        if (a == 10 || a == 127) return true
        if (a == 192 && b == 168) return true
        if (a == 172 && b in 16..31) return true
        if (a == 169 && b == 254) return true
        return false
    }
    if (bare.contains(':')) {
        val head = bare.substringBefore('%').substringBefore(':')
        if (Regex("""^fe[89ab][0-9a-f]{0,2}$""").matches(head)) return true
        if (Regex("""^f[cd][0-9a-f]{0,2}$""").matches(head)) return true
    }
    return false
}

fun resolveWebdavBaseUrl(
    url: String,
    lanUrl: String,
    remotePath: String,
    allowLan: Boolean,
    probe: (String) -> Boolean,
): String {
    val pub = url.trim().trimEnd('/')
    val storedLan = lanUrl.trim().trimEnd('/')
    if (!allowLan && storedLan.isNotEmpty() && pub.isEmpty()) throw WebdavException("lan_offline")
    val lan = if (allowLan) storedLan else ""
    if (lan.isEmpty()) {
        if (pub.isEmpty()) throw WebdavException("empty_url")
        return requirePublicWebdavUrl(pub)
    }
    val target = try {
        val folder = joinWebdavUrl(lan, resolveWebdavRemoteFolder(remotePath), allowPrivateHttp = true)
        if (folder.endsWith("/")) folder else "$folder/"
    } catch (err: Exception) {
        if (pub.isNotEmpty()) return requirePublicWebdavUrl(pub)
        if (err is WebdavException) throw err
        throw WebdavException("bad_url")
    }
    if (probe(target)) return lan
    if (pub.isEmpty()) throw WebdavException("lan_unreachable")
    return requirePublicWebdavUrl(pub)
}

/** The public field is https only. Private http belongs on the LAN address. */
fun requirePublicWebdavUrl(url: String): String {
    val root = try {
        URI(url)
    } catch (_: Exception) {
        throw WebdavException("bad_url")
    }
    if (root.scheme?.lowercase() != "https") throw WebdavException("https")
    return url
}

fun normalizeWebdavRemotePath(raw: String): String {
    val path = raw.trim().trimEnd('/')
    if (path.isEmpty() || path == WEBDAV_LEGACY_REMOTE_PATH || path == "/cursor-manager" || path == "/sync/Configs/cursor-manager") {
        return WEBDAV_DEFAULT_REMOTE_PATH
    }
    return resolveWebdavRemoteFolder(path)
}

fun resolveWebdavRemoteFolder(path: String): String {
    val trimmed = path.trim().trimEnd('/')
    if (trimmed.isEmpty()) return WEBDAV_DEFAULT_REMOTE_PATH
    if (trimmed.endsWith(".json", ignoreCase = true)) {
        val slash = trimmed.lastIndexOf('/')
        return if (slash <= 0) WEBDAV_DEFAULT_REMOTE_PATH else trimmed.substring(0, slash)
    }
    return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
}

fun joinWebdavUrl(base: String, remotePath: String, allowPrivateHttp: Boolean = false): String {
    val trimmed = base.trim()
    if (trimmed.isEmpty()) throw WebdavException("empty_url")
    val root = try {
        URI(trimmed)
    } catch (_: Exception) {
        throw WebdavException("bad_url")
    }
    val scheme = root.scheme?.lowercase()
    when {
        scheme == "https" -> Unit
        allowPrivateHttp && scheme == "http" && isPrivateWebdavHost(root.host ?: "") -> Unit
        allowPrivateHttp && scheme == "http" -> throw WebdavException("lan_http")
        else -> throw WebdavException("https")
    }
    val raw = remotePath.trim()
    if (
        raw.isEmpty() ||
        raw.contains('\u0000') ||
        raw.contains("..") ||
        raw.startsWith("//") ||
        Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*:""").containsMatchIn(raw)
    ) {
        throw WebdavException("bad_path")
    }
    val rel = raw.trimStart('/')
    if (rel.isEmpty()) throw WebdavException("empty_path")
    val encoded = rel.split('/').joinToString("/") { segment ->
        URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
    }
    val rootHref = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    val joined = try {
        URI(rootHref).resolve(encoded)
    } catch (_: Exception) {
        throw WebdavException("bad_url")
    }
    if (!sameWebdavOrigin(root, joined)) throw WebdavException("bad_path")
    val rootPath = (if (root.path.endsWith("/")) root.path else "${root.path}/").replace(Regex("/+"), "/")
    val outPath = joined.path.replace(Regex("/+"), "/")
    if (outPath != rootPath.trimEnd('/') && !outPath.startsWith(rootPath)) {
        throw WebdavException("bad_path")
    }
    return joined.toString()
}

private fun sameWebdavOrigin(root: URI, joined: URI): Boolean {
    if (root.scheme?.lowercase() != joined.scheme?.lowercase()) return false
    if (!root.host.equals(joined.host, ignoreCase = true)) return false
    val rootPort = if (root.port == -1) defaultWebdavPort(root.scheme) else root.port
    val joinedPort = if (joined.port == -1) defaultWebdavPort(joined.scheme) else joined.port
    return rootPort == joinedPort
}

private fun defaultWebdavPort(scheme: String?): Int {
    return if (scheme.equals("https", ignoreCase = true)) 443 else 80
}

fun normalizeWebdavEtag(etag: String?): String? {
    if (etag.isNullOrBlank()) return null
    var value = etag.trim()
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&apos;", "'", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
    if (value.length >= 2 && value.startsWith("W/", ignoreCase = true)) {
        value = value.substring(2).trim()
    }
    if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
        value = value.substring(1, value.length - 1)
    }
    return value.ifEmpty { null }
}

fun webdavEtagsMatch(left: String?, right: String?): Boolean {
    val a = normalizeWebdavEtag(left)
    val b = normalizeWebdavEtag(right)
    return a != null && a == b
}

fun firstGetEtag(xml: String): String? {
    return Regex("<(?:[\\w.-]+:)?getetag\\b[^>]*>\\s*([^<]*)", RegexOption.IGNORE_CASE)
        .find(xml)?.groupValues?.getOrNull(1)?.trim()?.ifEmpty { null }
}

fun folderUrl(base: String, remotePath: String, allowPrivateHttp: Boolean = false): String {
    val url = joinWebdavUrl(base, resolveWebdavRemoteFolder(remotePath), allowPrivateHttp)
    return if (url.endsWith("/")) url else "$url/"
}

fun fileUrl(base: String, remotePath: String, name: String, allowPrivateHttp: Boolean = false): String {
    return joinWebdavUrl(base, "${resolveWebdavRemoteFolder(remotePath)}/$name", allowPrivateHttp)
}

fun collectionUrlsBetween(rootUrl: String, fileUrl: String): List<String> {
    val root = URI(if (rootUrl.endsWith("/")) rootUrl else "$rootUrl/")
    val file = URI(fileUrl)
    val rootPath = root.path.trimEnd('/')
    val filePath = file.path.trimEnd('/')
    val rel = if (filePath.startsWith(rootPath)) filePath.substring(rootPath.length) else file.path
    val parts = rel.split("/").filter { it.isNotEmpty() }.dropLast(1)
    val out = mutableListOf<String>()
    var acc = if (root.toString().endsWith("/")) root.toString() else "${root}/"
    for (part in parts) {
        acc = URI(acc).resolve("$part/").toString()
        out.add(acc.trimEnd('/'))
    }
    return out
}

fun profileFileName(uuid: String): String = "$WEBDAV_PROFILES_DIR/$uuid.json"

fun isProfileFileName(name: String): Boolean {
    return name.startsWith("$WEBDAV_PROFILES_DIR/") &&
        name.endsWith(".json") &&
        !isWebdavTempFileName(name)
}

fun profileUuidFromFileName(name: String): String? {
    if (!isProfileFileName(name)) return null
    return name.removePrefix("$WEBDAV_PROFILES_DIR/").removeSuffix(".json")
}

fun ensureWebdavClientId(value: String?): String {
    val id = value?.trim()?.lowercase().orEmpty()
    return if (id.matches(Regex("^[0-9a-f]{8}$"))) id else createWebdavClientId()
}

fun createWebdavClientId(): String {
    val bytes = ByteArray(4)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

fun webdavTempFileName(name: String, clientId: String): String {
    val id = ensureWebdavClientId(clientId)
    val suffix = ".$id$WEBDAV_TMP_SUFFIX"
    return if (name.endsWith(suffix, ignoreCase = true)) name else "$name$suffix"
}

fun isWebdavTempFileName(name: String): Boolean = name.endsWith(WEBDAV_TMP_SUFFIX, ignoreCase = true)

fun parseHttpDate(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val patterns = arrayOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEEE, dd-MMM-yy HH:mm:ss zzz",
        "EEE MMM d HH:mm:ss yyyy",
    )
    for (pattern in patterns) {
        try {
            val format = SimpleDateFormat(pattern, Locale.US)
            format.timeZone = TimeZone.getTimeZone("GMT")
            format.isLenient = true
            return format.parse(raw.trim())?.time
        } catch (_: Exception) {
        }
    }
    return null
}

fun isStaleWebdavTemp(
    name: String,
    lastModified: String?,
    nowMs: Long,
    maxAgeMs: Long = WEBDAV_TMP_MAX_AGE_MS,
): Boolean {
    if (!isWebdavTempFileName(name)) return false
    val at = parseHttpDate(lastModified) ?: return false
    return nowMs - at >= maxAgeMs
}

fun safeRelativePath(path: String): String {
    val normalized = path.replace('\\', '/').trim().removePrefix("/")
    val parts = normalized.split('/')
    if (normalized.isEmpty() || parts.any { it.isEmpty() || it == "." || it == ".." }) {
        throw WebdavException("bad_path")
    }
    return normalized
}

fun isSyncedProfileFile(path: String): Boolean {
    return path == "config.yaml" || path.startsWith("providers/")
}

fun normalizeSubscriptionSource(source: String): String = source.trim().trimEnd('/')

fun subscriptionIdentity(type: String, source: String, uuid: String): String {
    val src = normalizeSubscriptionSource(source)
    if ((type == "Url" || type == "External") && src.isNotEmpty()) return "src:$src"
    return "id:$uuid"
}

fun parsePropfindEntries(xml: String, baseUrl: String): List<WebdavListEntry> {
    val base = URI(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
    val basePath = base.path.trimEnd('/')
    val blocks = xml.split(Regex("<(?:[\\w.-]+:)?response\\b", RegexOption.IGNORE_CASE)).drop(1)
    return blocks.mapNotNull { block ->
        val href = Regex("<(?:[\\w.-]+:)?href\\b[^>]*>\\s*([^<]+)", RegexOption.IGNORE_CASE)
            .find(block)?.groupValues?.getOrNull(1)?.trim() ?: return@mapNotNull null
        val abs = try {
            base.resolve(href)
        } catch (_: Exception) {
            return@mapNotNull null
        }
        val itemPath = abs.path.trimEnd('/')
        if (itemPath == basePath) return@mapNotNull null
        val rawName = itemPath.substringAfterLast('/').ifEmpty { return@mapNotNull null }
        val name = try {
            URLDecoder.decode(rawName, Charsets.UTF_8.name())
        } catch (_: Exception) {
            rawName
        }
        val etag = Regex("<(?:[\\w.-]+:)?getetag\\b[^>]*>\\s*([^<]*)", RegexOption.IGNORE_CASE)
            .find(block)?.groupValues?.getOrNull(1)?.trim()?.ifEmpty { null }
        val lastModified = Regex("<(?:[\\w.-]+:)?getlastmodified\\b[^>]*>\\s*([^<]*)", RegexOption.IGNORE_CASE)
            .find(block)?.groupValues?.getOrNull(1)?.trim()?.ifEmpty { null }
        val collection = Regex(
            "<(?:[\\w.-]+:)?resourcetype\\b[^>]*>[\\s\\S]*?<(?:[\\w.-]+:)?collection\\b",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(block)
        WebdavListEntry(abs.toString(), name, etag, collection, lastModified)
    }
}

fun extractLockTokens(xml: String): List<String> {
    return Regex(
        "<(?:[\\w.-]+:)?locktoken\\b[^>]*>[\\s\\S]*?<(?:[\\w.-]+:)?href\\b[^>]*>\\s*([^<\\s]+)",
        RegexOption.IGNORE_CASE,
    ).findAll(xml)
        .map { it.groupValues[1].trim().trim('<', '>') }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()
}
