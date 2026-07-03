package com.github.kr328.clash.service.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts kotlinx.serialization JsonElement trees (as returned by
 * `Clash.parseProfileSnapshot`) into plain Kotlin Map/List/scalar trees
 * that SnakeYAML can dump back to YAML without surprises.
 *
 * The WRITE helpers in [MihomoConfigDocument] still operate on raw Map/List/Any?
 * because they need to feed SnakeYAML;
 * READ now comes from the engine as JsonElement. This bridge lets the two
 * worlds coexist while we migrate read helpers piecewise.
 */
internal object JsonElementToYaml {
    fun convert(element: JsonElement?): Any? = when (element) {
        null, JsonNull -> null
        is JsonPrimitive -> convertPrimitive(element)
        is JsonObject -> convertObject(element)
        is JsonArray -> convertArray(element)
    }

    fun convertObject(obj: JsonObject): LinkedHashMap<String, Any?> {
        val out = LinkedHashMap<String, Any?>(obj.size)
        for ((key, value) in obj) {
            out[key] = convert(value)
        }
        return out
    }

    fun convertArray(arr: JsonArray): ArrayList<Any?> {
        val out = ArrayList<Any?>(arr.size)
        for (item in arr) {
            out.add(convert(item))
        }
        return out
    }

    /**
     * Convert a map of named objects (e.g. `proxy-providers`, `rule-providers`)
     * into the LinkedHashMap-of-LinkedHashMaps shape SnakeYAML emits as the
     * canonical block-style YAML, preserving insertion order.
     */
    fun convertObjectMap(map: Map<String, JsonObject>): LinkedHashMap<String, Any?> {
        val out = LinkedHashMap<String, Any?>(map.size)
        for ((key, value) in map) {
            out[key] = convertObject(value)
        }
        return out
    }

    /**
     * Convert a list of objects (e.g. `proxy-groups`, `proxies`, `listeners`)
     * into ArrayList<LinkedHashMap<String, Any?>>.
     */
    fun convertObjectList(list: List<JsonObject>): ArrayList<Any?> {
        val out = ArrayList<Any?>(list.size)
        for (item in list) {
            out.add(convertObject(item))
        }
        return out
    }

    private fun convertPrimitive(p: JsonPrimitive): Any? {
        if (p is JsonNull) return null
        if (p.isString) return p.content
        val raw = p.content
        // Order matters: booleans first, then integers (Long), then floats.
        // YAML 1.1 also accepts y/n/yes/no/on/off as booleans, but mihomo
        // configs use true/false exclusively — keep us aligned with mihomo.
        return when (raw) {
            "true" -> true
            "false" -> false
            else -> raw.toLongOrNull()
                ?: raw.toDoubleOrNull()
                ?: raw
        }
    }
}
