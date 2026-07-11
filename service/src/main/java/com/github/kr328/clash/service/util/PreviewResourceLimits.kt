package com.github.kr328.clash.service.util

import java.io.ByteArrayOutputStream
import java.io.File

/** Resource ceilings for data derived from an imported profile and returned to the UI. */
internal object PreviewResourceLimits {
    const val MAX_CONFIG_BYTES = 8L * 1024 * 1024
    const val MAX_PROVIDER_FILES = 64
    const val MAX_PROVIDER_FILE_BYTES = 2 * 1024 * 1024
    const val MAX_PROVIDER_TOTAL_BYTES = 8 * 1024 * 1024
    const val MAX_PROXY_ENTRIES_SCANNED = 8_192
    const val MAX_NAME_CHARS = 256
    const val MAX_GROUPS = 256
    const val MAX_MEMBERS_PER_GROUP = 1_024
    const val MAX_TOTAL_MEMBERS = 4_096
    const val MAX_TRANSPORTS = 2_048
    const val MAX_OUTPUT_CHARS = 128 * 1024
}

/** Per-preview budget. A skipped file degrades only UI metadata; the engine remains authoritative. */
internal class ProviderFileReadBudget {
    private var filesRead = 0
    private var bytesRead = 0

    fun readUtf8(file: File): String? {
        if (!file.isFile || filesRead >= PreviewResourceLimits.MAX_PROVIDER_FILES) return null
        val declaredSize = file.length()
        if (
            declaredSize < 0 ||
            declaredSize > PreviewResourceLimits.MAX_PROVIDER_FILE_BYTES ||
            declaredSize > PreviewResourceLimits.MAX_PROVIDER_TOTAL_BYTES - bytesRead
        ) return null

        val remaining = minOf(
            PreviewResourceLimits.MAX_PROVIDER_FILE_BYTES,
            PreviewResourceLimits.MAX_PROVIDER_TOTAL_BYTES - bytesRead,
        )
        val bytes = readAtMost(file, remaining) ?: return null
        filesRead++
        bytesRead += bytes.size
        return bytes.toString(Charsets.UTF_8)
    }

    private fun readAtMost(file: File, maxBytes: Int): ByteArray? {
        return runCatching {
            file.inputStream().buffered().use { input ->
                val output = ByteArrayOutputStream(minOf(file.length().coerceAtLeast(0).toInt(), maxBytes))
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - total))
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) return null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        }.getOrNull()
    }
}

internal class PreviewOutputBudget {
    private var chars = 0

    fun accept(value: String): Boolean = acceptAll(value)

    fun acceptAll(vararg values: String): Boolean {
        if (values.any { it.length > PreviewResourceLimits.MAX_NAME_CHARS }) return false
        val added = values.sumOf { it.length }
        if (added > PreviewResourceLimits.MAX_OUTPUT_CHARS - chars) return false
        chars += added
        return true
    }
}
