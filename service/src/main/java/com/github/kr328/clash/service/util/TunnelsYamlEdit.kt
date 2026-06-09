package com.github.kr328.clash.service.util

/**
 * Writes a profile's `tunnels:` block into `config.yaml` via the block-level
 * patcher, so comments, anchors and every unrelated section are preserved.
 * A non-empty config replaces the block; an empty one removes it (clean
 * teardown), never writing an empty list.
 */
object TunnelsYamlEdit {
    fun render(configText: String, config: TunnelsConfig): String {
        val block = config.toTunnelsBlock()
            ?: return MihomoConfigDocument.parseOrEmpty(configText).renderRemoving("tunnels")
        val document = MihomoConfigDocument.parseOrThrow(configText)
        document.root["tunnels"] = block
        return document.renderReplacing("tunnels")
    }

    /** Convenience: remove the block (master-toggle OFF) without a model. */
    fun renderCleared(configText: String): String =
        MihomoConfigDocument.parseOrEmpty(configText).renderRemoving("tunnels")
}
