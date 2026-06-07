package com.github.kr328.clash.service.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DnsHostsYamlEditTest {
    private val base = """
        # my profile
        mixed-port: 7890
        proxies:
          - {name: p1, type: socks5, server: 127.0.0.1, port: 1080}
        rules:
          - MATCH,p1
    """.trimIndent() + "\n"

    @Test
    fun adds_dns_and_hosts_when_absent_preserving_everything_else() {
        val cfg = DnsHostsConfig(
            enable = true,
            enhancedMode = "fake-ip",
            nameserver = listOf("https://1.1.1.1/dns-query"),
            hosts = linkedMapOf("example.com" to "1.2.3.4"),
        )
        val out = DnsHostsYamlEdit.render(base, cfg)

        // unrelated content preserved
        assertTrue(out.contains("# my profile"))
        assertTrue(out.contains("mixed-port: 7890"))
        assertTrue(out.contains("MATCH,p1"))
        // new blocks present
        assertTrue(out.contains("dns:"))
        assertTrue(out.contains("enhanced-mode: fake-ip"))
        assertTrue(out.contains("https://1.1.1.1/dns-query"))
        assertTrue(out.contains("hosts:"))
        assertTrue(out.contains("example.com"))
    }

    @Test
    fun replaces_existing_dns_without_touching_other_blocks() {
        val withDns = base + "dns:\n  enable: false\n  nameserver:\n    - 8.8.8.8\n"
        val cfg = DnsHostsConfig(enable = true, nameserver = listOf("1.1.1.1"))
        val out = DnsHostsYamlEdit.render(withDns, cfg)

        assertTrue(out.contains("1.1.1.1"))
        assertFalse(out.contains("8.8.8.8")) // old nameserver gone
        assertTrue(out.contains("MATCH,p1")) // rules intact
    }

    @Test
    fun preserves_unmodeled_dns_fields_on_save() {
        val richDns = base +
            "dns:\n" +
            "  enable: true\n" +
            "  respect-rules: true\n" +
            "  prefer-h3: false\n" +
            "  nameserver:\n    - 8.8.8.8\n" +
            "  nameserver-policy:\n    +.ru:\n      - https://dns.yandex/dns-query\n" +
            "  fake-ip-filter:\n    - '*.lan'\n"
        val cfg = DnsHostsConfig(enable = true, nameserver = listOf("1.1.1.1"))
        val out = DnsHostsYamlEdit.render(richDns, cfg)

        // managed field replaced
        assertTrue(out.contains("1.1.1.1"))
        assertFalse(out.contains("8.8.8.8"))
        // unmodeled fields preserved
        assertTrue(out.contains("respect-rules"))
        assertTrue(out.contains("nameserver-policy"))
        assertTrue(out.contains("fake-ip-filter"))
        assertTrue(out.contains("dns.yandex"))
    }

    @Test
    fun empty_model_removes_blocks_teardown() {
        val withBoth = base +
            "dns:\n  enable: true\n  nameserver:\n    - 1.1.1.1\n" +
            "hosts:\n  example.com: 1.2.3.4\n"
        val out = DnsHostsYamlEdit.render(withBoth, DnsHostsConfig())

        assertFalse(out.contains("dns:"))
        assertFalse(out.contains("hosts:"))
        assertFalse(out.contains("example.com"))
        // unrelated preserved
        assertTrue(out.contains("mixed-port: 7890"))
        assertTrue(out.contains("MATCH,p1"))
    }

    @Test
    fun render_cleared_removes_both_blocks() {
        val withBoth = base + "dns:\n  enable: true\n" + "hosts:\n  a.com: 1.1.1.1\n"
        val out = DnsHostsYamlEdit.renderCleared(withBoth)
        assertFalse(out.contains("dns:"))
        assertFalse(out.contains("hosts:"))
        assertTrue(out.contains("proxies:"))
    }
}
