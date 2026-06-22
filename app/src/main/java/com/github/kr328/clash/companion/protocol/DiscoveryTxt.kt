package com.github.kr328.clash.companion.protocol

/**
 * mDNS discovery TXT record (PROTOCOL.md §4.3). Exactly the keys app,id,name,ver,fp.
 * Encode emits them in the order app,id,name,ver,fp; decode tolerates any order.
 */
data class DiscoveryTxt(
    val app: String,
    val id: String,
    val name: String,
    val ver: Int,
    val fp: String,
) {
    /** TXT entries as `key=value` strings, in canonical emit order. */
    fun encode(): List<String> = listOf(
        "app=$app",
        "id=$id",
        "name=$name",
        "ver=$ver",
        "fp=$fp",
    )

    /** TXT attributes as a key->value map (for NsdServiceInfo.setAttribute). */
    fun attributes(): Map<String, String> = linkedMapOf(
        "app" to app,
        "id" to id,
        "name" to name,
        "ver" to ver.toString(),
        "fp" to fp,
    )

    companion object {
        /** Returns null if a required key is missing or `ver` is not an integer. */
        fun decode(entries: List<String>): DiscoveryTxt? {
            val map = HashMap<String, String>()
            for (entry in entries) {
                val eq = entry.indexOf('=')
                if (eq < 0) continue
                map[entry.substring(0, eq)] = entry.substring(eq + 1)
            }
            return fromMap(map)
        }

        fun fromMap(map: Map<String, String>): DiscoveryTxt? {
            val app = map["app"] ?: return null
            val id = map["id"] ?: return null
            val name = map["name"] ?: return null
            val ver = map["ver"]?.toIntOrNull() ?: return null
            val fp = map["fp"] ?: return null
            return DiscoveryTxt(app = app, id = id, name = name, ver = ver, fp = fp)
        }
    }
}
