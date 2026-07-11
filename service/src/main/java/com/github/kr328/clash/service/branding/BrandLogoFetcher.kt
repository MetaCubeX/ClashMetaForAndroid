package com.github.kr328.clash.service.branding

import android.content.Context
import android.graphics.BitmapFactory
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.log.LogRedaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
    private val client = OkHttpClient.Builder()
        .dns(PublicOnlyDns)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(context: Context, urlString: String): String? = withContext(Dispatchers.IO) {
        // LOG-1: never log the full URL (may carry operator tokens / query
        // params) — log only host/scheme, or redact when there's no host.
        val parsed = urlString.toHttpsUrlOrNull()
        if (parsed == null) {
            Log.w("BrandLogoFetcher: rejected invalid or non-https URL: ${LogRedaction.redactSuspicious(urlString)}")
            return@withContext null
        }

        try {
            val bytes = openWithSafeRedirects(parsed, 0) ?: return@withContext null
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

    private fun openWithSafeRedirects(url: HttpUrl, hop: Int): ByteArray? {
        if (hop > MAX_REDIRECTS) return null
        if (!url.isHttps) return null
        // OkHttp bypasses Dns.lookup for numeric hosts, so PublicOnlyDns alone
        // cannot protect localhost/LAN literals or redirects to them.
        if (!isAllowedLiteralHost(url.host)) return null

        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    in 200..299 -> readLogoResponse(response)
                    301, 302, 303, 307, 308 -> {
                        val location = response.header("Location") ?: return null
                        val resolved = url.resolve(location) ?: return null
                        openWithSafeRedirects(resolved, hop + 1)
                    }
                    else -> {
                        Log.w("BrandLogoFetcher HTTP ${response.code}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("BrandLogoFetcher connect failed: $e")
            null
        }
    }

    private fun readLogoResponse(response: Response): ByteArray? {
        val body = response.body ?: return null
        val ct = body.contentType()?.let { "${it.type}/${it.subtype}".lowercase() }
        if (ct == null || ct !in ALLOWED_CONTENT_TYPES) return null
        val length = body.contentLength()
        if (length in 1..Long.MAX_VALUE && length > MAX_BYTES) return null
        return body.byteStream().use { input ->
            input.readWithCap(MAX_BYTES + 1)
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

    private fun String.toHttpsUrlOrNull(): HttpUrl? {
        val parsed = toHttpUrlOrNull() ?: return null
        return parsed.takeIf { it.isHttps }
    }

    private object PublicOnlyDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isEmpty() || addresses.any { !it.isPublicAddress() }) {
                throw java.net.UnknownHostException("BrandLogoFetcher SSRF guard rejected host")
            }
            return addresses
        }
    }

    internal fun isAllowedLiteralHost(host: String): Boolean {
        val looksLikeIpv6 = ':' in host
        val looksNumeric = host.isNotEmpty() && host.all { it.isDigit() || it == '.' }
        if (!looksLikeIpv6 && !looksNumeric) return true
        if (looksNumeric && host.split('.').any { it.length > 1 && it.startsWith('0') }) return false
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        return address.isPublicAddress()
    }

    private fun InetAddress.isPublicAddress(): Boolean {
        if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress ||
            isSiteLocalAddress || isMulticastAddress) {
            return false
        }

        val bytes = address
        if (this is Inet4Address) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            return !(first == 0 ||
                first == 10 ||
                first == 100 && second in 64..127 ||
                first == 127 ||
                first == 169 && second == 254 ||
                first == 172 && second in 16..31 ||
                first == 192 && second == 168 ||
                first >= 224)
        }

        if (this is Inet6Address) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            return !(first == 0xFC || first == 0xFD ||
                first == 0xFE && (second and 0xC0) == 0x80)
        }

        return false
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
}
