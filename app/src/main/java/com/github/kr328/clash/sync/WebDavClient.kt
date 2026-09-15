package com.github.kr328.clash.sync

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * Minimal read-only WebDAV client compatible with Clash Verge's cloud backup layout.
 *
 * Clash Verge stores every backup as a zip inside a fixed sub-directory of the configured
 * WebDAV root (see clash-verge-rev: src-tauri/src/utils/dirs.rs `BACKUP_DIR`). We only need
 * to list that directory and download a zip, so just PROPFIND + GET are implemented.
 */
class WebDavClient(
    baseUrl: String,
    username: String,
    password: String,
) {
    private val root = baseUrl.trim().trimEnd('/')
    private val dirUrl = "$root/$BACKUP_DIR"
    private val credential = Credentials.basic(username, password)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * PROPFIND the backup directory and return the *.zip file names, newest first.
     * Verge names backups "<os>-backup-YYYY-MM-DD_HH-MM-SS.zip", so a descending
     * lexical sort puts the most recent backup first.
     */
    fun listBackups(): List<String> {
        val body = PROPFIND_BODY.toRequestBody("application/xml".toMediaType())
        val request = Request.Builder()
            .url("$dirUrl/")
            .header("Authorization", credential)
            .header("Depth", "1")
            .header("User-Agent", USER_AGENT)
            .method("PROPFIND", body)
            .build()

        client.newCall(request).execute().use { resp ->
            // Directory missing yet -> no backups instead of a hard failure.
            if (resp.code == 404) return emptyList()
            if (!resp.isSuccessful && resp.code != HTTP_MULTI_STATUS) {
                throw WebDavException("PROPFIND failed: HTTP ${resp.code}")
            }
            return parseZipHrefs(resp.body?.string().orEmpty())
        }
    }

    /** GET a backup zip by file name and return its raw bytes. */
    fun download(fileName: String): ByteArray {
        val request = Request.Builder()
            .url("$dirUrl/$fileName")
            .header("Authorization", credential)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw WebDavException("Download failed: HTTP ${resp.code}")
            return resp.body?.bytes() ?: throw WebDavException("Empty response body")
        }
    }

    private fun parseZipHrefs(xml: String): List<String> {
        return HREF_REGEX.findAll(xml)
            .map { it.groupValues[1].trim() }
            .map { it.substringAfterLast('/') }
            .map { decode(it) }
            .filter { it.endsWith(".zip", ignoreCase = true) }
            .distinct()
            .sortedDescending()
            .toList()
    }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (e: Exception) {
        s
    }

    companion object {
        // Keep in sync with clash-verge-rev `BACKUP_DIR`.
        const val BACKUP_DIR = "clash-verge-rev-backup"

        private const val TIMEOUT_SECONDS = 300L
        private const val HTTP_MULTI_STATUS = 207
        private const val USER_AGENT = "ClashMetaForAndroid WebDAV-Client"

        // <d:href>, <D:href> or <href> — capture the inner path, namespace prefix optional.
        private val HREF_REGEX =
            Regex("<(?:[\\w-]+:)?href>(.*?)</(?:[\\w-]+:)?href>", RegexOption.IGNORE_CASE)

        private const val PROPFIND_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<d:propfind xmlns:d=\"DAV:\"><d:prop>" +
                "<d:displayname/><d:getlastmodified/></d:prop></d:propfind>"
    }
}

class WebDavException(message: String) : Exception(message)
