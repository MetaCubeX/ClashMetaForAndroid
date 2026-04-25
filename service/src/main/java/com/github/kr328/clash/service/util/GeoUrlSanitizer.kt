package com.github.kr328.clash.service.util

import com.github.kr328.clash.common.log.Log
import java.io.File

/**
 * Rewrites broken `geox-url` entries inside a profile's `config.yaml` (and
 * any sibling rule/proxy provider files) so that the mihomo kernel does not
 * try to fetch GeoIP/GeoSite data from dead public mirrors such as
 * `mirror.ghproxy.com`.
 *
 * The sanitizer is idempotent: running it twice in a row produces no extra
 * disk writes when the file is already clean.
 */
object GeoUrlSanitizer {
    private val dumpYaml = YamlFormatting.blockYaml()

    /** Rewrite broken `geox-url` mirrors in [profileDir]/config.yaml. */
    fun sanitizeProfile(profileDir: File): Boolean {
        val configFile = File(profileDir, "config.yaml")
        if (!configFile.isFile) return false
        return try {
            val text = configFile.readText()
            val sanitized = sanitizeYaml(text) ?: return false
            if (sanitized == text) return false
            configFile.writeText(sanitized)
            Log.i("GeoUrlSanitizer: rewrote broken geox-url mirrors in ${configFile.name}")
            true
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
        val root = YamlFormatting.parseRootMap(text) ?: return null
        var changed = rewriteGeoxUrl(root)
        // Локальные .dat/.metadb/.mmdb уже распакованы из ассетов в clashDir,
        // поэтому отключаем периодический онлайн-апдейт ядром, иначе оно опять
        // упрётся в недоступный mirror.ghproxy.com и сломает старт.
        if (root["geo-auto-update"] == null) {
            root["geo-auto-update"] = false
            changed = true
        }
        return if (changed) dumpYaml.dump(root) else text
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
