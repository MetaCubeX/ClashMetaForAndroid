package com.github.kr328.clash.service.model

import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ConnectionMetadata
import com.github.kr328.clash.core.model.ConnectionTracker
import com.github.kr328.clash.core.model.ConnectionsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class RequestHistorySnapshot(
    val limit: Int = RequestHistoryStore.DEFAULT_LIMIT,
    val requests: List<RequestHistoryEntry> = emptyList(),
)

@Serializable
data class RequestHistoryEntry(
    val id: String = "",
    val timestamp: Long = 0,
    val host: String = "",
    val destination: String = "",
    val rule: String = "",
    val rulePayload: String = "",
    val proxy: String = "",
    val process: String = "",
    val uid: Int = 0,
    val network: String = "",
    val status: String = STATUS_ACTIVE,
) {
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.lowercase()
        return sequenceOf(
            id,
            host,
            destination,
            rule,
            rulePayload,
            proxy,
            process,
            uid.takeIf { it > 0 }?.toString().orEmpty(),
            network,
            status,
        ).any { it.lowercase().contains(q) }
    }

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_CLOSED = "closed"
    }
}

class RequestHistoryStore(private val limit: Int = DEFAULT_LIMIT) {
    init {
        require(limit > 0) { "limit must be positive" }
    }

    // LinkedHashMap gives O(1) lookup/update/remove while preserving insertion order.
    // The previous ArrayDeque-based implementation copied the whole deque on every
    // status flip — at 512 entries × ~50 active connections that was ~25k ops per
    // ingest cycle. put() on an existing key updates the value in place without
    // disturbing iteration order, which is exactly what we want for status updates.
    private val entries = LinkedHashMap<String, RequestHistoryEntry>()
    private val activeIds = linkedSetOf<String>()

    @Synchronized
    fun ingest(connections: List<ConnectionTracker>, now: Long = System.currentTimeMillis()) {
        val currentIds = connections.mapNotNullTo(linkedSetOf()) { it.id.takeIf(String::isNotBlank) }
        val closedIds = activeIds - currentIds

        closedIds.forEach { id ->
            entries[id]?.let { entries[id] = it.copy(status = RequestHistoryEntry.STATUS_CLOSED) }
        }
        activeIds.clear()
        activeIds.addAll(currentIds)

        connections.forEach { connection ->
            val id = connection.id
            if (id.isBlank()) return@forEach

            val existing = entries[id]
            val entry = connection.toRequestHistoryEntry(
                timestamp = existing?.timestamp ?: now,
                status = RequestHistoryEntry.STATUS_ACTIVE,
            )

            if (existing == null) {
                while (entries.size >= limit) {
                    val oldestId = entries.keys.iterator().next()
                    entries.remove(oldestId)
                    activeIds.remove(oldestId)
                }
            }
            entries[id] = entry
        }
    }

    @Synchronized
    fun snapshot(): RequestHistorySnapshot {
        return RequestHistorySnapshot(
            limit = limit,
            requests = entries.values.toList(),
        )
    }

    @Synchronized
    fun clear() {
        entries.clear()
        activeIds.clear()
    }

    companion object {
        const val DEFAULT_LIMIT = 512
    }
}

object RequestHistoryRepository {
    private val store = RequestHistoryStore()
    private val trackerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trackerLock = Any()
    private val ingestJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private var trackerJob: Job? = null
    private var trackerRefCount = 0
    @Volatile private var lastConsumerActivityAt: Long = 0L

    fun ingest(connections: List<ConnectionTracker>) = store.ingest(connections)

    fun snapshot(): RequestHistorySnapshot {
        // Each snapshot read counts as a live consumer signal. RequestHistoryActivity
        // polls every 2s while open; if those polls stop for IDLE_TIMEOUT_MS the
        // tracker assumes the consumer died without a clean stopTracking (process
        // kill, crash) and self-cancels to avoid a process-scoped battery leak.
        lastConsumerActivityAt = System.currentTimeMillis()
        return store.snapshot()
    }

    fun clear() = store.clear()

    /**
     * Begin sampling connection snapshots. Reference-counted: multiple consumers
     * can call startTracking and only the last stopTracking actually cancels the
     * ingest loop. Lets us keep the snapshot-poll work confined to the time a
     * UI surface is actually watching it instead of running on every TUN tick.
     */
    fun startTracking() {
        synchronized(trackerLock) {
            trackerRefCount++
            lastConsumerActivityAt = System.currentTimeMillis()
            if (trackerJob?.isActive == true) return
            trackerJob = trackerScope.launch {
                while (isActive) {
                    ingestSnapshot()
                    delay(SAMPLE_INTERVAL_MS)
                    val idleMs = System.currentTimeMillis() - lastConsumerActivityAt
                    if (idleMs > IDLE_TIMEOUT_MS) {
                        Log.w("Request history tracker idle for ${idleMs}ms; auto-stopping")
                        synchronized(trackerLock) {
                            trackerRefCount = 0
                            trackerJob = null
                        }
                        break
                    }
                }
            }
        }
    }

    fun stopTracking() {
        synchronized(trackerLock) {
            trackerRefCount = (trackerRefCount - 1).coerceAtLeast(0)
            if (trackerRefCount == 0) {
                trackerJob?.cancel()
                trackerJob = null
            }
        }
    }

    private fun ingestSnapshot() {
        val raw = runCatching { Clash.queryConnectionsSnapshot() }.getOrElse {
            Log.w("Request history snapshot query failed", it)
            return
        }
        val snap = runCatching {
            ingestJson.decodeFromString(ConnectionsSnapshot.serializer(), raw)
        }.getOrElse {
            Log.w("Request history snapshot decode failed; raw size=${raw.length}", it)
            return
        }
        store.ingest(snap.connections)
    }

    private const val SAMPLE_INTERVAL_MS = 2_000L
    private const val IDLE_TIMEOUT_MS = 30_000L
}

fun ConnectionTracker.toRequestHistoryEntry(
    timestamp: Long,
    status: String,
): RequestHistoryEntry {
    val m = metadata
    val host = firstNotBlank(m?.sniffHost, m?.host, m?.remoteDestination, m?.destinationIP)
    val destination = destination(m)
    val proxy = formatPolicy(chains, providerChains)

    return RequestHistoryEntry(
        id = id,
        timestamp = timestamp,
        host = host,
        destination = destination,
        rule = rule,
        rulePayload = rulePayload,
        proxy = proxy,
        process = m?.process.orEmpty(),
        uid = m?.uid ?: 0,
        network = m?.network.orEmpty(),
        status = status,
    )
}

fun formatRequestHistoryExport(entries: List<RequestHistoryEntry>): String {
    val header = listOf(
        "timestamp",
        "status",
        "network",
        "host",
        "destination",
        "rule",
        "rule_payload",
        "proxy",
        "process",
        "uid",
        "id",
    ).joinToString(",")

    return buildString {
        appendLine(header)
        entries.forEach { entry ->
            appendLine(
                listOf(
                    formatTimestamp(entry.timestamp),
                    entry.status,
                    entry.network,
                    entry.host,
                    entry.destination,
                    entry.rule,
                    entry.rulePayload,
                    entry.proxy,
                    entry.process,
                    entry.uid.takeIf { it > 0 }?.toString().orEmpty(),
                    entry.id,
                ).joinToString(",") { csvForExport(it) }
            )
        }
    }
}

private fun formatPolicy(chains: List<String>, providerChains: List<String>): String {
    if (chains.isEmpty() && providerChains.isEmpty()) return ""
    if (chains.isNotEmpty() && providerChains.isNotEmpty() && chains.size == providerChains.size) {
        return chains.mapIndexed { index, name ->
            val provider = providerChains[index].trim()
            if (provider.isBlank()) name else "$name [$provider]"
        }.joinToString(" > ")
    }
    if (chains.isNotEmpty()) return chains.joinToString(" > ")
    return providerChains.joinToString(" > ")
}

private fun destination(metadata: ConnectionMetadata?): String {
    if (metadata == null) return ""
    val remote = metadata.remoteDestination.takeIf(String::isNotBlank)
    if (remote != null) return remote
    val ip = metadata.destinationIP
    val port = metadata.destinationPort
    return when {
        ip.isBlank() -> ""
        port.isBlank() -> ip
        else -> "$ip:$port"
    }
}

private fun firstNotBlank(vararg values: String?): String {
    return values.firstOrNull { !it.isNullOrBlank() }.orEmpty()
}

internal fun csvForExport(value: String): String {
    val firstMeaningful = value.firstOrNull { !it.isWhitespace() }
    val safeValue = if (firstMeaningful in setOf('=', '+', '-', '@')) "'$value" else value
    val needsQuote = safeValue.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    if (!needsQuote) return safeValue
    return buildString {
        append('"')
        safeValue.forEach {
            if (it == '"') append("\"\"") else append(it)
        }
        append('"')
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(timestamp))
}
