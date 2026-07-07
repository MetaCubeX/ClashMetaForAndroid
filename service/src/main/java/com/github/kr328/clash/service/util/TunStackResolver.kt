package com.github.kr328.clash.service.util

/**
 * Resolves which TUN network stack to hand the VpnService fd (gvisor / system / mixed).
 *
 * Precedence:
 *  - An explicit app-setting choice (system / gvisor / mixed) is a user **override** and wins.
 *  - [AUTO] (the default) follows the *subscription*: the active profile's composed `config.yaml`
 *    declares `tun.stack`, so mixed→mixed, system→system, gvisor→gvisor. Falls back to [DEFAULT]
 *    when the subscription doesn't declare a stack (or declares an unrecognised one).
 */
object TunStackResolver {
    /** App-setting value meaning "follow the subscription". */
    const val AUTO = "auto"

    /** Used when Auto is selected but the subscription doesn't declare a usable stack. */
    private const val DEFAULT = "system"

    /** Stacks mihomo's TUN listener understands. An unknown value is ignored rather than crashing. */
    private val KNOWN = setOf("gvisor", "system", "mixed", "lwip")

    /**
     * @param configYaml the composed `config.yaml` the engine will load (subscription as-is + user
     *                   layer), or null when it can't be read.
     * @param setting    the app-setting stack: [AUTO] to follow the subscription, or an explicit
     *                   stack to override it.
     */
    fun resolve(configYaml: String?, setting: String): String {
        // Explicit user override (anything other than Auto) wins outright.
        if (setting != AUTO && setting in KNOWN) return setting
        // Auto → follow the subscription's declared tun.stack, falling back to the default.
        val declared = runCatching {
            val root = configYaml?.let { MihomoConfigDocument.parse(it)?.root }
            (root?.get("tun") as? Map<*, *>)?.get("stack")?.toString()?.trim()?.lowercase()
        }.getOrNull()
        return if (declared != null && declared in KNOWN) declared else DEFAULT
    }
}
