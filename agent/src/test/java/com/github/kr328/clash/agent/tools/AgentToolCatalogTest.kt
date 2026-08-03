package com.github.kr328.clash.agent.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentToolCatalogTest {
    @Test
    fun toolNamesAreUniqueAndNamespaced() {
        val tools = AgentToolCatalog.tools

        assertEquals(tools.size, tools.map(AgentToolDescriptor::name).toSet().size)
        assertTrue(tools.all { it.name.matches(Regex("[a-z]+(?:[._][a-z]+)+")) })
    }

    @Test
    fun destructiveOperationsHaveExplicitRiskAssignments() {
        assertNotNull(AgentToolCatalog.find("profiles.delete"))
        assertNotNull(AgentToolCatalog.find("tun.patch"))
        assertNotNull(AgentToolCatalog.find("connections.close_all"))
    }
}
