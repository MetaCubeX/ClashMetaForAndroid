package com.github.kr328.clash.sync

import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** A subscription profile recovered from a Clash Verge backup. */
data class RemoteProfile(val name: String, val url: String)

/** Result of parsing a backup: importable subscriptions and how many local configs were skipped. */
data class ParsedBackup(val remotes: List<RemoteProfile>, val skippedLocal: Int)

/**
 * Reads a Clash Verge backup zip and pulls out the parts CMFA can actually use.
 *
 * A backup zip contains `profiles.yaml` (the profile index) plus a `profiles/` directory.
 * Only `type: remote` entries (subscriptions, identified by their `url`) map onto a CMFA
 * profile. Verge-specific entries — local/merge/script/rules/proxies/groups — have no
 * equivalent here; `local` configs are counted so the user knows they were skipped, the
 * rest are sub-components and ignored silently.
 */
object VergeBackup {
    fun parse(zipBytes: ByteArray): ParsedBackup {
        val profilesYaml = readEntry(zipBytes, "profiles.yaml")
            ?: throw WebDavException("profiles.yaml not found in backup")

        @Suppress("UNCHECKED_CAST")
        val root = Yaml().load<Any?>(profilesYaml) as? Map<String, Any?>
            ?: return ParsedBackup(emptyList(), 0)
        val items = root["items"] as? List<*> ?: return ParsedBackup(emptyList(), 0)

        val remotes = ArrayList<RemoteProfile>()
        var skippedLocal = 0

        for (item in items) {
            val map = item as? Map<*, *> ?: continue
            when ((map["type"] as? String)?.lowercase()) {
                "remote" -> {
                    val url = (map["url"] as? String)?.trim().orEmpty()
                    if (url.isEmpty()) continue
                    val name = (map["name"] as? String)?.trim()?.ifEmpty { null } ?: url
                    remotes.add(RemoteProfile(name, url))
                }
                "local" -> skippedLocal++
            }
        }

        return ParsedBackup(remotes, skippedLocal)
    }

    private fun readEntry(zipBytes: ByteArray, name: String): String? {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.substringAfterLast('/') == name) {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        return null
    }
}
