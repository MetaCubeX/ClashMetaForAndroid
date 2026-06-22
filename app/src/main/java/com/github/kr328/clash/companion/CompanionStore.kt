package com.github.kr328.clash.companion

import android.content.Context
import android.os.Build
import com.github.kr328.clash.companion.protocol.Ids

/**
 * App-private persistent state for the companion feature. Runs in the app process
 * (the gateway service lives in `:app`), so a plain MODE_PRIVATE SharedPreferences is enough.
 *
 * Holds the stable [deviceId] (PROTOCOL.md §3.1), the user-facing [displayName], and the
 * default-OFF [agentEnabled] master toggle (§2). Pairings are kept separately in
 * [com.github.kr328.clash.companion.agent.PairingStore].
 */
class CompanionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("companion", Context.MODE_PRIVATE)

    /** Default OFF: while false the gateway must not listen and must not advertise (§2). */
    var agentEnabled: Boolean
        get() = prefs.getBoolean(KEY_AGENT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AGENT_ENABLED, value).apply()

    /** Stable device identifier, generated once and reused for the device's lifetime. */
    val deviceId: String
        get() {
            prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
            val generated = Ids.newDeviceId()
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
            return generated
        }

    /**
     * Persisted gateway port. 0 means "not chosen yet" → the service binds an ephemeral port and
     * writes the actual one back here, so the port stays stable across restarts and a previously
     * scanned QR keeps working (the ephemeral port would otherwise change on every restart).
     */
    var gatewayPort: Int
        get() = prefs.getInt(KEY_GATEWAY_PORT, 0)
        set(value) = prefs.edit().putInt(KEY_GATEWAY_PORT, value).apply()

    /** Human-readable display name (advertised over mDNS, shown in the QR). */
    var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, null) ?: defaultName()
        set(value) = prefs.edit().putString(KEY_DISPLAY_NAME, value).apply()

    private fun defaultName(): String {
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "ClashFest"
        return model
    }

    companion object {
        const val APP_ID = "clashfest"
        private const val KEY_AGENT_ENABLED = "agent_enabled"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_GATEWAY_PORT = "gateway_port"
    }
}
