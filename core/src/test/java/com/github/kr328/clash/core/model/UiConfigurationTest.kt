package com.github.kr328.clash.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class UiConfigurationTest {
    @Test
    fun absentFieldDefaultsToEmpty() {
        assertTrue(Json.decodeFromString(UiConfiguration.serializer(), "{}").routeExcludeAddress.isEmpty())
    }

    @Test
    fun snapshotBridgeDecodesBothFamilies() {
        val config = Json.decodeFromString(UiConfiguration.serializer(),
            """{"routeExcludeAddress":["192.0.2.0/24","2001:db8::/32"]}""")
        assertEquals(listOf("192.0.2.0/24", "2001:db8::/32"), config.routeExcludeAddress)
    }

    @Test
    fun emptyFieldDefaultsToUpstreamRoutes() {
        assertTrue(Json.decodeFromString(UiConfiguration.serializer(),
            """{"routeExcludeAddress":[]}""").routeExcludeAddress.isEmpty())
    }
}
