package com.github.kr328.clash.service.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AccessControlSelectionTest {
    private val strategy = AppsStrategyConfig(
        uuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        name = "work",
        mode = AccessControlMode.DenySelected,
        packages = listOf("com.example.work"),
    )

    @Test
    fun activeStrategyOverridesGlobalList() {
        val effective = resolveAccessControl(
            activeStrategy = strategy,
            globalMode = AccessControlMode.AcceptSelected,
            globalPackages = setOf("com.example.global"),
        )

        assertEquals(AccessControlMode.DenySelected, effective.mode)
        assertEquals(listOf("com.example.work"), effective.packages)
        assertFalse(globalAccessControlDrivesTunnel(strategy))
    }

    @Test
    fun globalListAppliesWithoutActiveStrategy() {
        val effective = resolveAccessControl(
            activeStrategy = null,
            globalMode = AccessControlMode.AcceptSelected,
            globalPackages = setOf("com.example.global"),
        )

        assertEquals(AccessControlMode.AcceptSelected, effective.mode)
        assertEquals(listOf("com.example.global"), effective.packages)
        assertTrue(globalAccessControlDrivesTunnel(null))
    }
}
