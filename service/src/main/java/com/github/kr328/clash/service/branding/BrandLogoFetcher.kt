package com.github.kr328.clash.service.branding

import android.content.Context
import android.graphics.BitmapFactory
import com.github.kr328.clash.common.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads an operator brand logo safely. Enforces every constraint listed
 * in docs/operator-api/security.md:
 *
 * - https only
 * - private IP / loopback / link-local rejected (SSRF guard, including post-redirect)
 * - Content-Type whitelist: image/png, image/webp, image/jpeg
 * - 512 KB hard size cap
 * - Bounds-only first decode rejects anything that would expand to >4 MB
 *   ARGB_8888 after full decode
 * - Result written atomically to <filesDir>/brand/<sha256(url)>.bin
 *
 * Returns the absolute path of the cached file on success, null on any failure.
 */
object BrandLogoFetcher {

    private const val MAX_BYTES = 512 * 1024
    private const val MAX_REDIRECTS = 3
    private const val MAX_DECODED_BYTES = 4 * 1024 * 1024
    private val ALLOWED_CONTENT_TYPES = setOf(
        "image/png",
        "image/webp",
        "image/jpeg",
        "image/jpg",
    )

    suspend fun fetch(context: Context, urlString: String): String? = withContext(Dispatchers.IO) {
        val parsed = runCatching { URL(urlString) }.getOrNull() ?: return@withContext null
        if (!parsed.protocol.equals("https", ignoreCase = true)) return@withContext null
        if (!isPublicHost(parsed.host)) return@withContext null

        try {
            val bytes = openWithSafeRedirects(parsed, 0)?.use { input ->
                input.readWithCap(MAX_BYTES + 1)
            } ?: return@withContext null
            if (bytes.size > MAX_BYTES) return@withContext null

            // Bounds-only decode to reject decompression bombs.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
            if (pixels * 4 > MAX_DECODED_BYTES) return@withContext null

            val dir = File(context.filesDir, "brand").apply { mkdirs() }
            val name = sha256Hex(urlString) + ".bin"
            val target = File(dir, name)
            val tmp = File(dir, "$name.tmp")
            tmp.outputStream().use { it.write(bytes) }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target.absolutePath
        } catch (e: Exception) {
            Log.w("BrandLogoFetcher failed: $e")
            null
        }
    }

    private fun openWithSafeRedirects(url: URL, hop: Int): InputStream? {
        if (hop > MAX_REDIRECTS) return null
        if (!url.protocol.equals("https", ignoreCase = true)) return null
        if (!isPublicHost(url.host)) return null

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = false
        }
        return try {
            conn.connect()
            when (val code = conn.responseCode) {
                in 200..299 -> {
                    val ct = conn.contentType?.substringBefore(';')?.trim()?.lowercase()
                    if (ct == null || ct !in ALLOWED_CONTENT_TYPES) {
                        conn.disconnect()
                        return null
                    }
                    val length = conn.contentLengthLong
                    if (length in 1..Long.MAX_VALUE && length > MAX_BYTES) {
                        conn.disconnect()
                        return null
                    }
                    SafeCloseStream(conn.inputStream, conn)
                }
                301, 302, 303, 307, 308 -> {
                    val next = conn.getHeaderField("Location") ?: return null.also { conn.disconnect() }
                    conn.disconnect()
                    val resolved = runCatching { URL(url, next) }.getOrNull() ?: return null
                    openWithSafeRedirects(resolved, hop + 1)
                }
                else -> {
                    conn.disconnect()
                    Log.w("BrandLogoFetcher HTTP $code")
                    null
                }
            }
        } catch (e: Exception) {
            conn.disconnect()
            Log.w("BrandLogoFetcher connect failed: $e")
            null
        }
    }

    /** Read at most [cap] bytes from the stream. Aborts cleanly if the stream is longer. */
    private fun InputStream.readWithCap(cap: Int): ByteArray {
        val buffer = ByteArray(8 * 1024)
        val out = java.io.ByteArrayOutputStream(minOf(cap, 64 * 1024))
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            total += read
            if (total > cap) {
                out.write(buffer, 0, cap - (total - read))
                return out.toByteArray()
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** Rejects private, loopback, link-local, and reserved IP space. SSRF guard. */
    private fun isPublicHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return try {
            val addr = InetAddress.getByName(host)
            !(addr.isAnyLocalAddress ||
                addr.isLoopbackAddress ||
                addr.isLinkLocalAddress ||
                addr.isSiteLocalAddress ||
                addr.isMulticastAddress ||
                isUniqueLocalIpv6(addr))
        } catch (_: Exception) {
            false
        }
    }

    private fun isUniqueLocalIpv6(addr: InetAddress): Boolean {
        if (addr.address.size != 16) return false
        // fc00::/7
        return (addr.address[0].toInt() and 0xFE) == 0xFC
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                append(HEX[v ushr 4]); append(HEX[v and 0x0F])
            }
        }
    }

    private val HEX = "0123456789abcdef".toCharArray()

    private class SafeCloseStream(
        private val delegate: InputStream,
        private val conn: HttpURLConnection,
    ) : InputStream() {
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun close() {
            try {
                delegate.close()
            } finally {
                conn.disconnect()
            }
        }
    }
}
