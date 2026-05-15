package com.github.kr328.clash.service.model

import com.github.kr328.clash.core.model.ConnectionMetadata
import com.github.kr328.clash.core.model.ConnectionTracker
import kotlinx.serialization.Serializable
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

    private val entries = ArrayDeque<RequestHistoryEntry>(limit)
    private val activeIds = linkedSetOf<String>()

    @Synchronized
    fun ingest(connections: List<ConnectionTracker>, now: Long = System.currentTimeMillis()) {
        val currentIds = connections.mapNotNullTo(linkedSetOf()) { it.id.takeIf(String::isNotBlank) }
        val closedIds = activeIds - currentIds

        closedIds.forEach { id ->
            replace(id) { it.copy(status = RequestHistoryEntry.STATUS_CLOSED) }
        }
        activeIds.clear()
        activeIds.addAll(currentIds)

        connections.forEach { connection ->
            val id = connection.id
            if (id.isBlank()) return@forEach

            val existing = entries.firstOrNull { it.id == id }
            val entry = connection.toRequestHistoryEntry(
                timestamp = existing?.timestamp ?: now,
                status = RequestHistoryEntry.STATUS_ACTIVE,
            )

            if (existing == null) {
                append(entry)
            } else {
                replace(id) { entry }
            }
        }
    }

    @Synchronized
    fun snapshot(): RequestHistorySnapshot {
        return RequestHistorySnapshot(
            limit = limit,
            requests = entries.toList(),
        )
    }

    @Synchronized
    fun clear() {
        entries.clear()
        activeIds.clear()
    }

    private fun append(entry: RequestHistoryEntry) {
        while (entries.size >= limit) {
            val removed = entries.removeFirst()
            activeIds.remove(removed.id)
        }
        entries.addLast(entry)
    }

    private fun replace(id: String, transform: (RequestHistoryEntry) -> RequestHistoryEntry) {
        val current = entries.toList()
        entries.clear()
        current.forEach { entry ->
            entries.addLast(if (entry.id == id) transform(entry) else entry)
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 512
    }
}

object RequestHistoryRepository {
    private val store = RequestHistoryStore()

    fun ingest(connections: List<ConnectionTracker>) {
        store.ingest(connections)
    }

    fun snapshot(): RequestHistorySnapshot {
        return store.snapshot()
    }

    fun clear() {
        store.clear()
    }
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
                ).joinToString(",") { csv(it) }
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

private fun csv(value: String): String {
    val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    if (!needsQuote) return value
    return buildString {
        append('"')
        value.forEach {
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
