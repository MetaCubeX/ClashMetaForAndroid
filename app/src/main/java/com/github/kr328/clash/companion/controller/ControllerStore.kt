package com.github.kr328.clash.companion.controller

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists the controller's paired agents (PROTOCOL.md §7.2 controller side). The raw bearer
 * [PairedAgent.token] must be kept (it is presented on every request) — stored app-private only.
 * Reconnect-by-deviceId (§4.4) updates [PairedAgent.host]/[PairedAgent.port] without re-pairing.
 */
class ControllerStore(context: Context) {
    @Serializable
    data class PairedAgent(
        val deviceId: String,
        val name: String,
        val app: String,
        val fp: String,
        val token: String,
        val host: String,
        val port: Int,
    )

    private val prefs = context.applicationContext
        .getSharedPreferences("companion_controller", Context.MODE_PRIVATE)

    fun list(): List<PairedAgent> = load()

    fun get(deviceId: String): PairedAgent? = load().firstOrNull { it.deviceId == deviceId }

    /** Upsert a pairing (re-pair or first pair), keyed by the agent's deviceId. */
    fun put(agent: PairedAgent) {
        save(load().filterNot { it.deviceId == agent.deviceId } + agent)
    }

    /** Update the last-known address for a paired agent (reconnect-by-deviceId). */
    fun updateAddress(deviceId: String, host: String, port: Int) {
        save(load().map { if (it.deviceId == deviceId) it.copy(host = host, port = port) else it })
    }

    fun remove(deviceId: String) {
        save(load().filterNot { it.deviceId == deviceId })
    }

    private fun load(): List<PairedAgent> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(agents: List<PairedAgent>) {
        prefs.edit().putString(KEY, json.encodeToString(agents)).apply()
    }

    private companion object {
        const val KEY = "paired_agents"
        val json = Json { ignoreUnknownKeys = true }
    }
}
