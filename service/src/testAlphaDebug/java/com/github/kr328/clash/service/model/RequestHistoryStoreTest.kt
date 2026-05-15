package com.github.kr328.clash.service.model

import com.github.kr328.clash.core.model.ConnectionMetadata
import com.github.kr328.clash.core.model.ConnectionTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestHistoryStoreTest {
    @Test
    fun dropsOldestOverLimit() {
        val store = RequestHistoryStore(limit = 2)

        store.ingest(listOf(tracker("1")), now = 1)
        store.ingest(listOf(tracker("1"), tracker("2")), now = 2)
        store.ingest(listOf(tracker("1"), tracker("2"), tracker("3")), now = 3)

        val ids = store.snapshot().requests.map { it.id }
        assertEquals(listOf("2", "3"), ids)
    }

    @Test
    fun clearRemovesEntriesAndActiveState() {
        val store = RequestHistoryStore(limit = 4)

        store.ingest(listOf(tracker("1")), now = 1)
        store.clear()
        store.ingest(emptyList(), now = 2)

        assertTrue(store.snapshot().requests.isEmpty())
    }

    @Test
    fun marksMissingActiveConnectionsClosed() {
        val store = RequestHistoryStore(limit = 4)

        store.ingest(listOf(tracker("1")), now = 1)
        store.ingest(emptyList(), now = 2)

        val entry = store.snapshot().requests.single()
        assertEquals(RequestHistoryEntry.STATUS_CLOSED, entry.status)
    }

    @Test
    fun exportEscapesCsvFields() {
        val text = formatRequestHistoryExport(
            listOf(
                RequestHistoryEntry(
                    id = "id-1",
                    timestamp = 0,
                    host = "example.com",
                    destination = "1.1.1.1:443",
                    rule = "DOMAIN-SUFFIX",
                    rulePayload = "example.com",
                    proxy = "Proxy, Group",
                    process = "app\"name",
                    uid = 1000,
                    network = "tcp",
                    status = RequestHistoryEntry.STATUS_ACTIVE,
                )
            )
        )

        assertTrue(text.startsWith("timestamp,status,network,host"))
        assertTrue(text.contains("\"Proxy, Group\""))
        assertTrue(text.contains("\"app\"\"name\""))
        assertFalse(text.contains("null"))
    }

    private fun tracker(id: String): ConnectionTracker {
        return ConnectionTracker(
            id = id,
            rule = "MATCH",
            chains = listOf("DIRECT"),
            metadata = ConnectionMetadata(
                host = "example$id.com",
                network = "tcp",
                process = "com.example.$id",
                uid = id.toIntOrNull() ?: 0,
                destinationIP = "1.1.1.$id",
                destinationPort = "443",
            ),
        )
    }
}
