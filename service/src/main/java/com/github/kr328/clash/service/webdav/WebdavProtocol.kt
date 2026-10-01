package com.github.kr328.clash.service.webdav

import java.net.URI
import java.net.URLDecoder
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

const val WEBDAV_SYNC_KIND = "clash-meta-android-webdav-sync"
const val WEBDAV_PROFILE_KIND = "clash-meta-profile"
const val WEBDAV_SYNC_VERSION = 1
const val WEBDAV_META_FILE = "_sync.json"
const val WEBDAV_PROFILES_DIR = "profiles"
const val WEBDAV_DEFAULT_REMOTE_PATH = "/clash-meta"
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

fun resolveWebdavRemoteFolder(path: String): String {
    val trimmed = path.trim().trimEnd('/')
    if (trimmed.isEmpty()) return WEBDAV_DEFAULT_REMOTE_PATH
    if (trimmed.endsWith(".json", ignoreCase = true)) {
        val slash = trimmed.lastIndexOf('/')
        return if (slash <= 0) WEBDAV_DEFAULT_REMOTE_PATH else trimmed.substring(0, slash)
    }
    return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
}

fun joinWebdavUrl(base: String, remotePath: String): String {
    val trimmed = base.trim()
    if (trimmed.isEmpty()) throw WebdavException("empty_url")
    val root = try {
        URI(trimmed)
    } catch (_: Exception) {
        throw WebdavException("bad_url")
    }
    if (root.scheme?.lowercase() != "https") throw WebdavException("https")
    val rel = remotePath.trim().trimStart('/')
    if (rel.isEmpty()) throw WebdavException("empty_path")
    val rootHref = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    return URI(rootHref).resolve(rel).toString()
}

fun folderUrl(base: String, remotePath: String): String {
    val url = joinWebdavUrl(base, resolveWebdavRemoteFolder(remotePath))
    return if (url.endsWith("/")) url else "$url/"
}

fun fileUrl(base: String, remotePath: String, name: String): String {
    return joinWebdavUrl(base, "${resolveWebdavRemoteFolder(remotePath)}/$name")
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
