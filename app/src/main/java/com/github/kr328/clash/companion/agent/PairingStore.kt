package com.github.kr328.clash.companion.agent

import android.content.Context
import com.github.kr328.clash.companion.protocol.Ids
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists per-controller pairings on the agent (PROTOCOL.md §7). Stores only `SHA-256(token)`
 * (lowercase hex) — never the raw token. Authentication hashes the presented bearer token and
 * compares constant-time.
 *
 * In the QR flow the token is minted and embedded in the QR before the controller is known, so
 * pairings are keyed by [Pairing.tokenHash]; the controller's identity ([Pairing.controllerId],
 * [Pairing.label]) is filled opportunistically from request headers on first authenticated use.
 */
class PairingStore(context: Context) {
    @Serializable
    data class Pairing(
        val tokenHash: String,
        val controllerId: String? = null,
        val label: String? = null,
        val pairedAt: Long,
    )

    private val prefs = context.applicationContext
        .getSharedPreferences("companion_pairings", Context.MODE_PRIVATE)

    fun list(): List<Pairing> = load()

    /**
     * Mint a fresh token, persisting only its hash. Returns the raw token, which is handed to the
     * controller exactly once (embedded in the QR) and is never stored here.
     */
    fun issueToken(): String {
        val token = Ids.newToken()
        val pairing = Pairing(tokenHash = Ids.tokenHash(token), pairedAt = System.currentTimeMillis())
        save(load() + pairing)
        return token
    }

    /** Returns the matching pairing for a presented bearer token, or null if unknown/revoked. */
    fun authenticate(token: String): Pairing? {
        val hash = Ids.tokenHash(token)
        return load().firstOrNull { Ids.hashesEqual(it.tokenHash, hash) }
    }

    /** Record the controller's advertised identity on a paired entry (best-effort, for the UI). */
    fun recordController(tokenHash: String, controllerId: String?, label: String?) {
        if (controllerId == null && label == null) return
        val updated = load().map {
            if (it.tokenHash == tokenHash) {
                it.copy(
                    controllerId = controllerId ?: it.controllerId,
                    label = label ?: it.label,
                )
            } else {
                it
            }
        }
        save(updated)
    }

    /** Un-pair (revoke) a controller by its token hash. Its token no longer authenticates (§7.3). */
    fun revoke(tokenHash: String) {
        save(load().filterNot { it.tokenHash == tokenHash })
    }

    private fun load(): List<Pairing> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(pairings: List<Pairing>) {
        prefs.edit().putString(KEY, json.encodeToString(pairings)).apply()
    }

    private companion object {
        const val KEY = "pairings"
        val json = Json { ignoreUnknownKeys = true }
    }
}
