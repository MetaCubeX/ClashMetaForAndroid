package com.github.kr328.clash.service.util

import java.math.BigInteger
import java.net.Inet6Address
import java.net.InetAddress

/** Pure address-set planning without Android APIs or DNS lookups. */
object VpnRoutePlanner {
    const val MAX_ROUTES = 512

    enum class Mode { UNCHANGED, NATIVE, CIDR_COMPLEMENT }

    data class Plan(
        val includes: List<Prefix>,
        val excludes: List<Prefix>,
        val requestedExcludes: List<Prefix>,
        val mode: Mode,
    )

    data class Prefix private constructor(
        val network: BigInteger,
        val length: Int,
        val bits: Int,
    ) {
        val address: InetAddress
            get() {
                val raw = network.toByteArray()
                val bytes = ByteArray(bits / 8)
                val count = minOf(bytes.size, raw.size)
                raw.copyInto(bytes, bytes.size - count, raw.size - count)
                return if (bits == 128) Inet6Address.getByAddress(null, bytes, -1)
                else InetAddress.getByAddress(bytes)
            }

        fun contains(other: Prefix): Boolean = bits == other.bits && length <= other.length &&
            network == mask(other.network, length, bits)

        fun children(): List<Prefix> {
            check(length < bits)
            return listOf(
                Prefix(network, length + 1, bits),
                Prefix(network.setBit(bits - length - 1), length + 1, bits),
            )
        }

        override fun toString(): String = "${address.hostAddress}/$length"

        companion object {
            fun parse(value: String): Prefix {
                // Never put untrusted configuration text in an error or resolve a hostname.
                val parts = value.split('/')
                require(parts.size == 2) { "Invalid VPN route CIDR" }
                require(parts[1].isNotEmpty() && parts[1].all { it in '0'..'9' }) {
                    "Invalid VPN route prefix length"
                }
                val length = parts[1].toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid VPN route prefix length")
                val bytes = if (':' in parts[0]) ipv6(parts[0]) else ipv4(parts[0])
                val bits = bytes.size * 8
                require(length in 0..bits) { "Invalid VPN route prefix length" }
                return Prefix(mask(BigInteger(1, bytes), length, bits), length, bits)
            }

            private fun ipv4(value: String): ByteArray {
                val parts = value.split('.')
                require(parts.size == 4) { "Invalid VPN IPv4 address" }
                return parts.map {
                    require(it.isNotEmpty() && it.length <= 3 && it.all { c -> c in '0'..'9' }) {
                        "Invalid VPN IPv4 address"
                    }
                    require(it.length == 1 || it[0] != '0') { "Invalid VPN IPv4 address" }
                    val octet = it.toInt()
                    require(octet in 0..255) { "Invalid VPN IPv4 address" }
                    octet.toByte()
                }.toByteArray()
            }

            private fun ipv6(value: String): ByteArray {
                var text = value
                if ('.' in text) {
                    val tail = ipv4(text.substringAfterLast(':'))
                    val high = ((tail[0].toInt() and 255) shl 8) or (tail[1].toInt() and 255)
                    val low = ((tail[2].toInt() and 255) shl 8) or (tail[3].toInt() and 255)
                    text = text.substringBeforeLast(':') + ":${high.toString(16)}:${low.toString(16)}"
                }
                val halves = text.split("::")
                require(halves.size <= 2) { "Invalid VPN IPv6 address" }
                fun words(part: String): List<Int> = if (part.isEmpty()) emptyList() else
                    part.split(':').map {
                        require(it.length in 1..4 && it.all { c -> c in "0123456789abcdefABCDEF" }) {
                            "Invalid VPN IPv6 address"
                        }
                        it.toInt(16)
                    }
                val left = words(halves[0])
                val right = if (halves.size == 2) words(halves[1]) else emptyList()
                val missing = 8 - left.size - right.size
                require(if (halves.size == 2) missing > 0 else missing == 0) { "Invalid VPN IPv6 address" }
                val groups = left + List(missing) { 0 } + right
                return ByteArray(16) { i -> (groups[i / 2] shr (if (i % 2 == 0) 8 else 0)).toByte() }
            }

            private fun mask(value: BigInteger, length: Int, bits: Int): BigInteger =
                value.shiftRight(bits - length).shiftLeft(bits - length)
        }
    }

    fun plan(includes: List<String>, excludes: List<String>, native: Boolean): Plan {
        require(includes.size <= MAX_ROUTES && excludes.size <= MAX_ROUTES) {
            "VPN route input exceeds $MAX_ROUTES entries"
        }
        val original = includes.map(Prefix::parse)
        // Preserve original order and duplicates when the field is absent or empty.
        if (excludes.isEmpty()) return Plan(original, emptyList(), emptyList(), Mode.UNCHANGED)
        val requested = compact(excludes.map(Prefix::parse))
        val base = compact(original)
        // An exclusion must not implicitly enable a disabled address family.
        val relevant = requested.filter { e -> base.any { it.contains(e) || e.contains(it) } }
        if (native) {
            // More-specific includes would otherwise override a broad native exclusion.
            val routes = base.filterNot { include -> relevant.any { it.contains(include) } }
            checkSize(routes.size + relevant.size)
            return Plan(routes, relevant, requested, Mode.NATIVE)
        }
        var routes = base
        for (exclude in relevant) {
            val next = mutableListOf<Prefix>()
            fun subtract(include: Prefix) {
                when {
                    exclude.contains(include) -> Unit
                    include.contains(exclude) -> include.children().forEach(::subtract)
                    else -> {
                        checkSize(next.size + 1)
                        next.add(include)
                    }
                }
            }
            routes.forEach(::subtract)
            routes = next
        }
        return Plan(routes, emptyList(), requested, Mode.CIDR_COMPLEMENT)
    }

    private fun checkSize(size: Int) {
        require(size <= MAX_ROUTES) { "VPN route plan exceeds $MAX_ROUTES entries" }
    }

    private fun compact(prefixes: List<Prefix>): List<Prefix> {
        val result = mutableListOf<Prefix>()
        prefixes.distinct().sortedWith(compareBy({ it.bits }, { it.length }, { it.network })).forEach { p ->
            if (result.none { it.contains(p) }) result.add(p)
        }
        return result.sortedWith(compareBy({ it.bits }, { it.network }, { it.length }))
    }
}
