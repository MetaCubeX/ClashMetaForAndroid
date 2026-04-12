package com.github.kr328.clash.service

import android.content.Context
import android.net.Uri
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Pending
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.processingDir
import com.github.kr328.clash.service.util.sendProfileChanged
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit

object ProfileProcessor {
    private val profileLock = Mutex()
    private val processLock = Mutex()
    private const val subscriptionUserAgent = "Ninja/2.10.4"
    private val contentDispositionFilename = Regex(
        """filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?""",
        RegexOption.IGNORE_CASE
    )

    data class ParsedHeaders(
        val interval: Long,
        val name: String,
        val upload: Long,
        val download: Long,
        val total: Long,
        val expire: Long,
        val home: String?,
        val crisp: String?,
    )

    suspend fun apply(context: Context, uuid: UUID, callback: IFetchObserver? = null) {
        withContext(NonCancellable) {
            processLock.withLock {
                val snapshot = profileLock.withLock {
                    val pending = PendingDao().queryByUUID(uuid)
                        ?: throw IllegalArgumentException("profile $uuid not found")

                    pending.enforceFieldValid()

                    context.processingDir.deleteRecursively()
                    context.processingDir.mkdirs()

                    context.pendingDir.resolve(pending.uuid.toString())
                        .copyRecursively(context.processingDir, overwrite = true)

                    pending
                }

                val force = snapshot.type != Profile.Type.File
                var cb = callback

                if (snapshot.type == Profile.Type.Url && snapshot.source.isRemoteUrl()) {
                    downloadRemoteProfile(context.processingDir.resolve("config.yaml"), snapshot.source)
                }

                processNinjaProxies(context.processingDir)

                Clash.fetchAndValid(context.processingDir, snapshot.source, force && !snapshot.source.isRemoteUrl()) {
                    try {
                        cb?.updateStatus(it)
                    } catch (e: Exception) {
                        cb = null

                        Log.w("Report fetch status: $e", e)
                    }
                }.await()

                profileLock.withLock {
                    if (PendingDao().queryByUUID(snapshot.uuid) == snapshot) {
                        context.importedDir.resolve(snapshot.uuid.toString())
                            .deleteRecursively()
                        context.processingDir
                            .copyRecursively(context.importedDir.resolve(snapshot.uuid.toString()))

                        val old = ImportedDao().queryByUUID(snapshot.uuid)
                        var parsed = ParsedHeaders(
                            interval = snapshot.interval,
                            name = snapshot.name,
                            upload = snapshot.upload,
                            download = snapshot.download,
                            total = snapshot.total,
                            expire = snapshot.expire,
                            home = snapshot.home,
                            crisp = snapshot.crisp
                        )
                        if (snapshot?.type == Profile.Type.Url) {
                            if (snapshot.source.isRemoteUrl()) {
                                val client = OkHttpClient()
                                val request = Request.Builder()
                                    .url(snapshot.source)
                                    .header("User-Agent", subscriptionUserAgent)
                                    .build()

                                client.newCall(request).execute().use { response ->
                                    parsed = parseResponseHeaders(
                                        response = response,
                                        interval = parsed.interval,
                                        name = parsed.name,
                                        upload = parsed.upload,
                                        download = parsed.download,
                                        total = parsed.total,
                                        expire = parsed.expire,
                                        home = parsed.home,
                                        crisp = parsed.crisp
                                    )
                                }
                            }
                            val new = Imported(
                                snapshot.uuid,
                                parsed.name,
                                snapshot.type,
                                snapshot.source,
                                parsed.interval,
                                parsed.upload,
                                parsed.download,
                                parsed.total,
                                parsed.expire,
                                parsed.home,
                                parsed.crisp,
                                old?.createdAt ?: System.currentTimeMillis()
                            )
                            if (old != null) {
                                ImportedDao().update(new)
                            } else {
                                ImportedDao().insert(new)
                            }

                            PendingDao().remove(snapshot.uuid)

                            context.pendingDir.resolve(snapshot.uuid.toString())
                                .deleteRecursively()

                            context.sendProfileChanged(snapshot.uuid)
                        } else if (snapshot?.type == Profile.Type.File) {
                            val new = Imported(
                                snapshot.uuid,
                                snapshot.name,
                                snapshot.type,
                                snapshot.source,
                                snapshot.interval,
                                parsed.upload,
                                parsed.download,
                                parsed.total,
                                parsed.expire,
                                parsed.home,
                                parsed.crisp,
                                old?.createdAt ?: System.currentTimeMillis()
                            )
                            if (old != null) {
                                ImportedDao().update(new)
                            } else {
                                ImportedDao().insert(new)
                            }

                            PendingDao().remove(snapshot.uuid)

                            context.pendingDir.resolve(snapshot.uuid.toString())
                                .deleteRecursively()

                            context.sendProfileChanged(snapshot.uuid)
                        }
                    }
                }
            }
        }
    }

    suspend fun update(context: Context, uuid: UUID, callback: IFetchObserver?) {
        withContext(NonCancellable) {
            processLock.withLock {
                val snapshot = profileLock.withLock {
                    val imported = ImportedDao().queryByUUID(uuid)
                        ?: throw IllegalArgumentException("profile $uuid not found")

                    context.processingDir.deleteRecursively()
                    context.processingDir.mkdirs()

                    context.importedDir.resolve(imported.uuid.toString())
                        .copyRecursively(context.processingDir, overwrite = true)

                    imported
                }

                var cb = callback

                if (snapshot.source.isRemoteUrl()) {
                    downloadRemoteProfile(context.processingDir.resolve("config.yaml"), snapshot.source)
                }

                processNinjaProxies(context.processingDir)

                Clash.fetchAndValid(context.processingDir, snapshot.source, !snapshot.source.isRemoteUrl()) {
                    try {
                        cb?.updateStatus(it)
                    } catch (e: Exception) {
                        cb = null

                        Log.w("Report fetch status: $e", e)
                    }
                }.await()

                profileLock.withLock {
                    if (ImportedDao().exists(snapshot.uuid)) {
                        context.importedDir.resolve(snapshot.uuid.toString()).deleteRecursively()
                        context.processingDir
                            .copyRecursively(context.importedDir.resolve(snapshot.uuid.toString()))

                        context.sendProfileChanged(snapshot.uuid)
                    }
                }
            }
        }
    }

    suspend fun delete(context: Context, uuid: UUID) {
        withContext(NonCancellable) {
            profileLock.withLock {
                ImportedDao().remove(uuid)
                PendingDao().remove(uuid)

                val pending = context.pendingDir.resolve(uuid.toString())
                val imported = context.importedDir.resolve(uuid.toString())

                pending.deleteRecursively()
                imported.deleteRecursively()

                context.sendProfileChanged(uuid)
            }
        }
    }

    suspend fun release(context: Context, uuid: UUID): Boolean {
        return withContext(NonCancellable) {
            profileLock.withLock {
                PendingDao().remove(uuid)

                context.pendingDir.resolve(uuid.toString()).deleteRecursively()
            }
        }
    }

    suspend fun active(context: Context, uuid: UUID) {
        withContext(NonCancellable) {
            profileLock.withLock {
                if (ImportedDao().exists(uuid)) {
                    val store = ServiceStore(context)

                    store.activeProfile = uuid

                    context.sendProfileChanged(uuid)
                }
            }
        }
    }

    private fun Pending.enforceFieldValid() {
        val scheme = Uri.parse(source)?.scheme?.lowercase(Locale.getDefault())

        when {
            name.isBlank() ->
                throw IllegalArgumentException("Empty name")

            source.isEmpty() && type != Profile.Type.File ->
                throw IllegalArgumentException("Invalid url")

            source.isNotEmpty() && scheme != "https" && scheme != "http" && scheme != "content" ->
                throw IllegalArgumentException("Unsupported url $source")

            interval != 0L && TimeUnit.MILLISECONDS.toMinutes(interval) < 15 ->
                throw IllegalArgumentException("Invalid interval")
        }
    }

    fun parseResponseHeaders(
        response: Response,
        interval: Long,
        name: String,
        upload: Long,
        download: Long,
        total: Long,
        expire: Long,
        home: String?,
        crisp: String?,
    ): ParsedHeaders {
        var nextInterval = interval
        var nextName = name
        var nextUpload = upload
        var nextDownload = download
        var nextTotal = total
        var nextExpire = expire
        var nextHome = home
        var nextCrisp = crisp

        response.header("subscription-userinfo")?.let { userinfo ->
            userinfo.split(";").forEach { flag ->
                val info = flag.split("=", limit = 2)
                if (info.size != 2) return@forEach

                val key = info[0].trim()
                val value = info[1].trim()
                if (value.isEmpty()) return@forEach

                when {
                    key.contains("upload", true) -> nextUpload = parseLongValue(value) ?: nextUpload
                    key.contains("download", true) -> nextDownload = parseLongValue(value) ?: nextDownload
                    key.contains("total", true) -> nextTotal = parseLongValue(value) ?: nextTotal
                    key.contains("expire", true) -> nextExpire = parseExpireValue(value) ?: nextExpire
                }
            }
        }

        response.header("profile-update-interval")
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?.let { nextInterval = TimeUnit.HOURS.toMillis(it) }

        parseProfileName(response)?.let { nextName = it }
        response.header("profile-web-page-url")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { nextHome = it }
        response.header("support-url")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { nextCrisp = it }

        return ParsedHeaders(
            interval = nextInterval,
            name = nextName,
            upload = nextUpload,
            download = nextDownload,
            total = nextTotal,
            expire = nextExpire,
            home = nextHome,
            crisp = nextCrisp
        )
    }

    private fun parseProfileName(response: Response): String? {
        response.header("profile-title")
            ?.let(::decodeHeaderText)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val contentDisposition = response.header("content-disposition") ?: return null
        val matched = contentDispositionFilename.find(contentDisposition) ?: return null
        val encoded = matched.groups[1]?.value ?: matched.groups[2]?.value ?: return null

        return decodeHeaderText(encoded).takeIf { it.isNotEmpty() }
    }

    private fun decodeHeaderText(value: String): String {
        val normalized = value.trim().removeSurrounding("\"")
        val decoded = runCatching {
            URLDecoder.decode(normalized, StandardCharsets.UTF_8.name())
        }.getOrDefault(normalized)

        if (!decoded.startsWith("base64:", true)) {
            return decoded
        }

        val base64Payload = decoded.substringAfter(':')
        return runCatching {
            String(Base64.getDecoder().decode(base64Payload), StandardCharsets.UTF_8)
        }.getOrDefault(decoded)
    }

    private fun parseLongValue(value: String): Long? {
        return runCatching {
            BigDecimal(value.substringBefore('.')).longValueExact()
        }.getOrNull()
    }

    private fun parseExpireValue(value: String): Long? {
        return runCatching {
            (value.toDouble() * 1000).toLong()
        }.getOrNull()
    }

    private fun String.isRemoteUrl(): Boolean {
        return startsWith("https://", true) || startsWith("http://", true)
    }

    private fun downloadRemoteProfile(target: File, source: String) {
        val request = Request.Builder()
            .url(source)
            .header("User-Agent", subscriptionUserAgent)
            .build()

        OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Fetch profile failed: HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("Fetch profile failed: empty body")
            target.parentFile?.mkdirs()

            target.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun processNinjaProxies(root: File) {
        runCatching {
            val config = root.resolve("config.yaml")
            if (!config.exists()) {
                return
            }

            val yaml = Yaml(DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            })
            val document = yaml.load<Any?>(config.readText(Charsets.UTF_8)) as? Map<Any?, Any?> ?: return
            val proxies = document["proxies"] as? List<*> ?: return

            var changed = false
            val rewritten = proxies.map { entry ->
                val proxy = entry as? Map<Any?, Any?> ?: return@map entry
                val type = proxy["type"] as? String
                val tls = proxy["tls"] as? Boolean ?: false

                if (type != "ninja" || !tls) {
                    return@map entry
                }

                try {
                    val decoded = NinjaDecoder.decode(
                        NinjaDecoder.Input(
                            server = proxy["server"] as? String ?: return@map entry,
                            port = (proxy["port"] as? Number)?.toInt() ?: return@map entry,
                            password = proxy["password"] as? String ?: return@map entry,
                            nodePassword = proxy["node_password"] as? String ?: return@map entry
                        )
                    )

                    changed = true
                    LinkedHashMap(proxy).apply {
                        this["server"] = decoded.server
                        this["port"] = decoded.port
                        this["node_password"] = decoded.nodePassword
                        remove("tls")
                    }
                } catch (_: Exception) {
                    entry
                }
            }

            if (!changed) {
                return
            }

            config.writeText(
                yaml.dump(
                    LinkedHashMap(document).apply {
                        this["proxies"] = rewritten
                    }
                ),
                Charsets.UTF_8
            )
        }
    }
}
