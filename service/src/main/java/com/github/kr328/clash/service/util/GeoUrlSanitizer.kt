package com.github.kr328.clash.service.util

import com.github.kr328.clash.common.log.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Rewrites broken `geox-url` entries inside a profile's `config.yaml` (and
 * any sibling rule/proxy provider files) so that the mihomo kernel does not
 * try to fetch GeoIP/GeoSite data from dead public mirrors such as
 * `mirror.ghproxy.com`.
 *
 * The sanitizer is idempotent: running it twice in a row produces no extra
 * disk writes when the file is already clean. A small in-memory mtime+size
 * cache also lets repeated [sanitizeProfile] calls (e.g. on every override
 * patch) skip the YAML read+parse entirely when the file has not changed
 * since the last clean pass.
 */
object GeoUrlSanitizer {
    /** Last-known clean signature per absolute config.yaml path. */
    private val cleanSignature = ConcurrentHashMap<String, Long>()

    private fun signatureOf(file: File): Long =
        // Pack mtime (high 40 bits) and size (low 24 bits) into a single Long. Collisions
        // are harmless: a stale hit only means we re-run sanitize on identical content.
        (file.lastModified() shl 24) xor (file.length() and 0xFFFFFFL)

    /** Rewrite broken `geox-url` mirrors in [profileDir]/config.yaml. */
    fun sanitizeProfile(profileDir: File): Boolean {
        val configFile = File(profileDir, "config.yaml")
        if (!configFile.isFile) return false
        val path = configFile.absolutePath
        val sig = signatureOf(configFile)
        if (cleanSignature[path] == sig) {
            // Already known clean; skip read + YAML parse entirely.
            return false
        }
        return try {
            val text = configFile.readText()
            val sanitized = sanitizeYaml(text)
            if (sanitized == null) {
                false
            } else if (sanitized == text) {
                cleanSignature[path] = sig
                false
            } else {
                configFile.writeText(sanitized)
                cleanSignature[path] = signatureOf(configFile)
                Log.i("GeoUrlSanitizer: rewrote broken geox-url mirrors in ${configFile.name}")
                true
            }
        } catch (e: Exception) {
            Log.w("GeoUrlSanitizer: failed to sanitize ${configFile.absolutePath}: ${e.message}", e)
            false
        }
    }

    /**
     * Returns the sanitized YAML text, or `null` if the input is not a parseable
     * top-level map. Returns the original [text] unchanged when nothing needs
     * to be rewritten.
     */
    fun sanitizeYaml(text: String): String? {
        val document = MihomoConfigDocument.parse(text) ?: return null
        val root = document.root
        var changed = rewriteGeoxUrl(root)
        // Локальные .dat/.metadb/.mmdb уже распакованы из ассетов в clashDir,
        // поэтому отключаем периодический онлайн-апдейт ядром, иначе оно опять
        // упрётся в недоступный mirror.ghproxy.com и сломает старт.
        if (root["geo-auto-update"] == null) {
            root["geo-auto-update"] = false
            changed = true
        }
        return if (changed) document.renderReplacing("geox-url", "geo-auto-update") else text
    }

    private fun rewriteGeoxUrl(root: MutableMap<String, Any?>): Boolean {
        val raw = root["geox-url"] as? Map<*, *> ?: return false
        val mutable = LinkedHashMap<String, Any?>(raw.size)
        for ((k, v) in raw) {
            mutable[k?.toString() ?: continue] = v
        }
        var changed = false
        listOf(
            "geoip" to GeoMirrors.GeoKind.GeoIp,
            "geosite" to GeoMirrors.GeoKind.GeoSite,
            "mmdb" to GeoMirrors.GeoKind.GeoIpMmdb,
            "asn" to GeoMirrors.GeoKind.GeoIpMmdb,
        ).forEach { (key, kind) ->
            val current = mutable[key]?.toString()
            if (GeoMirrors.isBroken(current)) {
                val replacement = GeoMirrors.sanitize(current, kind)
                if (!replacement.isNullOrBlank() && replacement != current) {
                    mutable[key] = replacement
                    changed = true
                    Log.i("GeoUrlSanitizer: $key '$current' -> '$replacement'")
                }
            }
        }
        if (changed) {
            root["geox-url"] = mutable
        }
        return changed
    }
}
