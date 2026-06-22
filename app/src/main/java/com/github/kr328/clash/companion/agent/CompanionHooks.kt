package com.github.kr328.clash.companion.agent

/**
 * App-only operations the mihomo core cannot perform (PROTOCOL.md §1, §8). The gateway server is
 * decoupled from Android specifics through this interface; the concrete implementation wires them
 * to the existing VPN start/stop, subscription import, status snapshot and rename.
 */
interface CompanionHooks {
    /** Current tunnel power state: `"on"` or `"off"`. */
    fun powerState(): String

    /**
     * Apply a power action. [action] is one of `on`/`off`/`toggle` (already validated).
     * Returns the resulting state (`"on"`/`"off"`).
     * @throws PowerUnavailable when the VPN cannot be started (e.g. consent not granted on device).
     */
    fun power(action: String): String

    /**
     * Import a shared subscription. Exactly one of [url]/[payload] is non-null (already validated).
     * @throws PayloadUnsupported if an inline [payload] is given but this agent only supports url.
     */
    fun importSubscription(url: String?, payload: String?, name: String)

    /** Persist the new display name and re-announce mDNS (§8.5). */
    fun rename(name: String)

    class PowerUnavailable(message: String) : Exception(message)
    class PayloadUnsupported(message: String) : Exception(message)
}
