package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.util.VpnRoutePlanner.Prefix
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.util.Random
import javax.xml.parsers.DocumentBuilderFactory

class VpnRoutePlannerTest {
    private val v4 = listOf("0.0.0.0/0")
    private val dual = v4 + "::/0"

    private fun routed(plan: VpnRoutePlanner.Plan, address: String): Boolean {
        val host = Prefix.parse("$address/${if (':' in address) 128 else 32}")
        val include = plan.includes.filter { it.contains(host) }.maxOfOrNull { it.length } ?: -1
        val exclude = plan.excludes.filter { it.contains(host) }.maxOfOrNull { it.length } ?: -1
        return include >= 0 && include > exclude
    }

    private fun both(includes: List<String> = v4, excludes: List<String>, check: (VpnRoutePlanner.Plan) -> Unit) {
        listOf(false, true).forEach { check(VpnRoutePlanner.plan(includes, excludes, it)) }
    }

    @Test
    fun ipv4SubnetPreservesNeighbors() = both(excludes = listOf("198.18.0.0/16")) {
        assertFalse(routed(it, "198.18.1.42"))
        assertTrue(routed(it, "8.8.8.8"))
        assertTrue(routed(it, "198.17.255.255"))
        assertTrue(routed(it, "198.19.0.0"))
    }

    @Test
    fun ipv4HostExcludesOnlyTarget() = both(excludes = listOf("198.18.1.42/32")) {
        assertFalse(routed(it, "198.18.1.42"))
        assertTrue(routed(it, "198.18.1.41"))
        assertTrue(routed(it, "198.18.1.43"))
    }

    @Test
    fun multipleExclusions() = both(excludes = listOf("10.0.0.0/8", "192.0.2.0/24")) {
        assertFalse(routed(it, "10.1.2.3"))
        assertFalse(routed(it, "192.0.2.255"))
        assertTrue(routed(it, "192.0.3.0"))
    }

    @Test
    fun overlappingExclusions() = both(excludes = listOf("10.0.0.0/8", "10.1.0.0/16")) {
        assertEquals(1, it.requestedExcludes.size)
        assertFalse(routed(it, "10.2.0.1"))
    }

    @Test
    fun duplicatesAndHostBits() = both(excludes = listOf("192.0.2.9/24", "192.0.2.0/24")) {
        assertEquals(listOf(Prefix.parse("192.0.2.0/24")), it.requestedExcludes)
    }

    @Test
    fun malformedRejectedWithoutEcho() {
        val invalid = listOf("invalid.example/24", "1.2.3.4", "1.2.3.999/32", "1.2.3.4/-1",
            "1.2.3.4/33", "1.2.3.4/+1", "01.2.3.4/8", "1.2.3.4/999999999999",
            "::/129", "1::2::3/64", "fe80::1%wlan0/64", "gg::/32", "/0", ":::/64",
            "1:2:3:4:5:6:7:8:9/64", "[::1]/128", "1.2.3.4/32/0")
        for (bad in invalid) for (native in listOf(false, true)) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                VpnRoutePlanner.plan(dual, listOf(bad), native)
            }
            assertFalse(error.message.orEmpty().contains(bad))
        }
    }

    @Test
    fun ipv6SubnetAndHost() = both(dual, listOf("2001:db8::/32", "2001:db9::1/128")) {
        assertFalse(routed(it, "2001:db8:1234::1"))
        assertFalse(routed(it, "2001:db9::1"))
        assertTrue(routed(it, "2001:db9::2"))
        assertTrue(routed(it, "8.8.8.8"))
    }

    @Test
    fun emptyPreservesOrderAndDuplicates() {
        val routes = listOf("192.0.2.0/24", "0.0.0.0/0", "192.0.2.0/24", "::/0")
        both(routes, emptyList()) {
            assertEquals(routes.map(Prefix::parse), it.includes)
            assertEquals(VpnRoutePlanner.Mode.UNCHANGED, it.mode)
            assertTrue(it.excludes.isEmpty())
        }
    }

    @Test
    fun allFamilyExcluded() = both(dual + "172.19.0.2/32" + "fdfe:dcba:9876::2/128", dual) {
        assertTrue(it.includes.isEmpty())
        assertFalse(routed(it, "172.19.0.2"))
        assertFalse(routed(it, "fdfe:dcba:9876::2"))
    }

    @Test
    fun nativeBroadExclusionBeatsSpecificInclude() = both(
        listOf("0.0.0.0/0", "172.19.0.2/32"), listOf("172.16.0.0/12")
    ) {
        assertFalse(routed(it, "172.19.0.2"))
        assertTrue(routed(it, "172.15.255.255"))
    }

    @Test
    fun disabledFamilyIsNotEnabled() = both(v4, listOf("::/0")) {
        assertEquals(v4.map(Prefix::parse), it.includes)
        assertTrue(it.excludes.isEmpty())
        assertFalse(routed(it, "2001:db8::1"))
    }

    @Test
    fun disjointExclusionDoesNotWidenIncludes() = both(listOf("192.0.2.0/24"), listOf("10.0.0.0/8")) {
        assertTrue(routed(it, "192.0.2.1"))
        assertFalse(routed(it, "8.8.8.8"))
    }

    @Test
    fun exactCoverageAndNoOverlap() {
        for ((include, exclude, expectedCount) in listOf(
            Triple("0.0.0.0/0", "198.18.0.0/16", 16),
            Triple("::/0", "2001:db8::1/128", 128)
        )) {
            val plan = VpnRoutePlanner.plan(listOf(include), listOf(exclude), false)
            val excluded = Prefix.parse(exclude)
            assertEquals(expectedCount, plan.includes.size)
            plan.includes.forEachIndexed { index, p ->
                assertFalse(p.contains(excluded) || excluded.contains(p))
                plan.includes.drop(index + 1).forEach { q -> assertFalse(p.contains(q) || q.contains(p)) }
            }
            val size = plan.includes.fold(BigInteger.ZERO) { sum, p -> sum + BigInteger.ONE.shiftLeft(p.bits - p.length) }
            assertEquals(BigInteger.ONE.shiftLeft(excluded.bits) - BigInteger.ONE.shiftLeft(excluded.bits - excluded.length), size)
        }
    }

    @Test
    fun exhaustiveSmallSpaceAndDeterminism() {
        val excludes = listOf("192.0.2.16/28", "192.0.2.128/26", "192.0.2.255/32", "192.0.2.17/32")
        val a = VpnRoutePlanner.plan(listOf("192.0.2.0/24"), excludes, false)
        assertEquals(a, VpnRoutePlanner.plan(listOf("192.0.2.0/24"), excludes.reversed(), false))
        both(listOf("192.0.2.0/24"), excludes) { plan ->
            (0..255).forEach { n -> assertEquals(n !in 16..31 && n !in 128..191 && n != 255, routed(plan, "192.0.2.$n")) }
        }
    }

    @Test
    fun randomMembershipMatchesAddressSet() {
        val random = Random(5)
        repeat(20) {
            val exclusions = List(8) { "${random.nextInt(256)}.${random.nextInt(256)}.0.0/${8 + random.nextInt(9)}" }
            val masks = exclusions.map(Prefix::parse)
            both(dual, exclusions) { plan ->
                repeat(100) {
                    val address = List(4) { random.nextInt(256) }.joinToString(".")
                    val host = Prefix.parse("$address/32")
                    assertEquals(masks.none { it.contains(host) }, routed(plan, address))
                }
            }
        }
    }

    @Test
    fun boundedRouteGrowth() {
        assertThrows(IllegalArgumentException::class.java) {
            VpnRoutePlanner.plan(v4, List(513) { "192.0.2.1/32" }, false)
        }
        val fragmented = List(32) { "${it * 7}.1.2.3/32" }
        assertThrows(IllegalArgumentException::class.java) { VpnRoutePlanner.plan(v4, fragmented, false) }
        assertEquals(32, VpnRoutePlanner.plan(v4, fragmented, true).excludes.size)
    }

    @Test
    fun ipv4MappedIpv6RemainsIpv6() {
        val p = Prefix.parse("::ffff:192.0.2.1/128")
        assertEquals(128, p.bits)
        assertEquals(16, p.address.address.size)
        assertEquals(Prefix.parse("0:0:0:0:0:ffff:c000:201/128"), p)
    }

    @Test
    fun privateBypassResourcesAndDnsPreserved() {
        val root = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "service/src/main/res/values/arrays.xml").exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(root, "service/src/main/res/values/arrays.xml"))
        val arrays = doc.getElementsByTagName("string-array")
        val routes = mutableListOf<String>()
        for (i in 0 until arrays.length) {
            val children = arrays.item(i).childNodes
            for (j in 0 until children.length) if (children.item(j).nodeName == "item") routes.add(children.item(j).textContent)
        }
        routes.addAll(listOf("172.19.0.2/32", "fdfe:dcba:9876::2/128"))
        both(routes, emptyList()) { assertEquals(routes.map(Prefix::parse), it.includes) }
        both(routes, listOf("198.18.0.0/16", "2001:db8::/32")) {
            assertFalse(routed(it, "10.1.2.3"))
            assertFalse(routed(it, "172.16.1.1"))
            assertFalse(routed(it, "fc00::1"))
            assertFalse(routed(it, "198.18.1.42"))
            assertFalse(routed(it, "2001:db8::1"))
            assertTrue(routed(it, "172.19.0.2"))
            assertTrue(routed(it, "fdfe:dcba:9876::2"))
            assertTrue(routed(it, "8.8.8.8"))
        }
    }
}
