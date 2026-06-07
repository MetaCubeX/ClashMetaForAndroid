package com.github.kr328.clash.service.util

/**
 * Writes a profile's name-resolution config into `config.yaml`, touching only
 * the `dns:` and top-level `hosts:` blocks. Uses the block-level patcher so
 * comments, anchors and every unrelated section are preserved.
 *
 * A non-null block is set/replaced; a null block (the model serialized nothing,
 * e.g. master-toggle teardown) is **removed** from the file rather than written
 * as an empty map.
 */
object DnsHostsYamlEdit {
    /**
     * @return the proposed `config.yaml` text after applying [config]. If
     * [config] is empty, both blocks are removed (clean teardown).
     */
    fun render(configText: String, config: DnsHostsConfig): String {
        val replaceKeys = ArrayList<String>(2)
        val removeKeys = ArrayList<String>(2)

        val document = MihomoConfigDocument.parseOrThrow(configText)

        // DNS: overlay managed fields onto the existing block, keeping unknown
        // keys (respect-rules, nameserver-policy, fake-ip-filter, ...) intact.
        val dns = LinkedHashMap<String, Any?>()
        (document.root["dns"] as? Map<*, *>)?.forEach { (k, v) -> dns[k.toString()] = v }
        config.mergeIntoDns(dns)
        if (dns.isNotEmpty()) {
            document.root["dns"] = dns
            replaceKeys += "dns"
        } else {
            removeKeys += "dns"
        }

        // Hosts is a flat map this editor fully owns: replace, or drop if empty.
        val hosts = config.toHostsBlock()
        if (hosts != null) {
            document.root["hosts"] = hosts
            replaceKeys += "hosts"
        } else {
            removeKeys += "hosts"
        }

        var text = configText
        if (replaceKeys.isNotEmpty()) {
            text = document.renderReplacing(*replaceKeys.toTypedArray())
        }
        if (removeKeys.isNotEmpty()) {
            text = MihomoConfigDocument.parseOrEmpty(text).renderRemoving(*removeKeys.toTypedArray())
        }
        return text
    }

    /** Convenience: remove both blocks (master-toggle OFF) without a model. */
    fun renderCleared(configText: String): String =
        MihomoConfigDocument.parseOrEmpty(configText).renderRemoving("dns", "hosts")
}
