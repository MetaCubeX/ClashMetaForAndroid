package com.github.kr328.clash.agent.tools

import com.github.kr328.clash.agent.authorization.AgentOperationRisk
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentExecutableToolsTest {
    @Test
    fun executableToolNamesAndSchemasAreValid() {
        val tools = AgentExecutableTools.all
        assertEquals(tools.size, tools.map(AgentToolSpec::name).toSet().size)
        assertTrue(tools.size >= 25)
        tools.forEach { tool ->
            assertTrue(tool.name.matches(Regex("[a-z][a-z0-9_]+")), tool.name)
            assertEquals("object", tool.parameters["type"]?.jsonPrimitive?.content)
            val properties = tool.parameters["properties"]?.jsonObject ?: error(tool.name)
            val required = tool.parameters["required"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            assertTrue(required.all(properties::containsKey), tool.name)
        }
    }

    @Test
    fun readsAreSafeAndIrreversibleChangesAreCritical() {
        val byName = AgentExecutableTools.all.associateBy(AgentToolSpec::name)
        listOf("profiles_list", "profile_read_config", "installed_apps", "network_info", "runtime_status", "connections_list")
            .forEach { assertEquals(AgentOperationRisk.READ_ONLY, byName.getValue(it).risk) }
        assertEquals(AgentOperationRisk.CRITICAL, byName.getValue("profile_delete").risk)
        assertFalse(byName.getValue("profile_replace_config").risk == AgentOperationRisk.READ_ONLY)
    }
}
