package com.github.kr328.clash.companion.protocol

/**
 * Pairing payload (PROTOCOL.md §6.1). Canonical URI:
 *
 *   clashctl-pair://<ip>:<port>?id=<id>&name=<name>&app=<app>&fp=<fp>&token=<token>
 *
 * Encode emits parameters in the fixed order id,name,app,fp,token with uppercase %XX
 * percent-encoding of anything outside the RFC 3986 unreserved set. Decode accepts any order
 * and rejects a payload missing ip/port/id/fp/token (name/app optional).
 */
data class PairingPayload(
    val ip: String,
    val port: Int,
    val id: String,
    val fp: String,
    val token: String,
    val name: String? = null,
    val app: String? = null,
) {
    fun toUri(): String {
        val host = if (ip.contains(':')) "[$ip]" else ip // bracket IPv6 literals
        val sb = StringBuilder("clashctl-pair://").append(host).append(':').append(port).append('?')
        val params = buildList {
            add("id" to id)
            if (name != null) add("name" to name)
            if (app != null) add("app" to app)
            add("fp" to fp)
            add("token" to token)
        }
        params.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append('&')
            sb.append(k).append('=').append(percentEncode(v))
        }
        return sb.toString()
    }

    companion object {
        private const val SCHEME = "clashctl-pair://"
        private val UNRESERVED =
            ('A'..'Z').toSet() + ('a'..'z') + ('0'..'9') + setOf('-', '.', '_', '~')

        /** Returns null when the payload is malformed or missing a required field. */
        fun parse(uri: String): PairingPayload? {
            if (!uri.startsWith(SCHEME)) return null
            val rest = uri.substring(SCHEME.length)
            val q = rest.indexOf('?')
            if (q < 0) return null
            val authority = rest.substring(0, q)
            val query = rest.substring(q + 1)

            val (ip, port) = parseAuthority(authority) ?: return null

            val params = HashMap<String, String>()
            for (pair in query.split('&')) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                if (eq < 0) continue
                val key = pair.substring(0, eq)
                val value = percentDecode(pair.substring(eq + 1))
                params[key] = value
            }

            val id = params["id"]
            val fp = params["fp"]
            val token = params["token"]
            if (id.isNullOrEmpty() || fp.isNullOrEmpty() || token.isNullOrEmpty()) return null

            return PairingPayload(
                ip = ip,
                port = port,
                id = id,
                fp = fp,
                token = token,
                name = params["name"]?.takeIf { it.isNotEmpty() },
                app = params["app"]?.takeIf { it.isNotEmpty() },
            )
        }

        private fun parseAuthority(authority: String): Pair<String, Int>? {
            if (authority.startsWith("[")) {
                val close = authority.indexOf(']')
                if (close < 0) return null
                val ip = authority.substring(1, close)
                if (authority.getOrNull(close + 1) != ':') return null
                val port = authority.substring(close + 2).toIntOrNull() ?: return null
                if (ip.isEmpty()) return null
                return ip to port
            }
            val colon = authority.lastIndexOf(':')
            if (colon < 0) return null // port is required
            val ip = authority.substring(0, colon)
            val port = authority.substring(colon + 1).toIntOrNull() ?: return null
            if (ip.isEmpty()) return null
            return ip to port
        }

        private fun percentEncode(value: String): String {
            val sb = StringBuilder()
            for (b in value.toByteArray(Charsets.UTF_8)) {
                val c = (b.toInt() and 0xFF).toChar()
                if (c in UNRESERVED) sb.append(c)
                else sb.append('%').append("%02X".format(b.toInt() and 0xFF))
            }
            return sb.toString()
        }

        private fun percentDecode(value: String): String {
            val out = java.io.ByteArrayOutputStream(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '%' && i + 2 < value.length) {
                    out.write(((Character.digit(value[i + 1], 16) shl 4) or
                        Character.digit(value[i + 2], 16)))
                    i += 3
                } else {
                    out.write(c.code)
                    i++
                }
            }
            return out.toString("UTF-8")
        }
    }
}
