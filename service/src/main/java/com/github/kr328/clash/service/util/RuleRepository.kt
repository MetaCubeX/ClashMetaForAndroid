package com.github.kr328.clash.service.util

import android.content.Context
import com.github.kr328.clash.service.model.RuleSource
import com.github.kr328.clash.service.model.RuleState
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class RuleRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun load(uuid: UUID, configText: String): RuleState {
        val parsed = RuleMapper.parseStateFromConfig(configText)
        val file = stateFile(uuid)
        if (file.isFile) {
            runCatching {
                val stored = json.decodeFromString(RuleState.serializer(), file.readText())
                val merged = syncProviderRules(stored, parsed)
                save(uuid, merged)
                return merged
            }
        }
        save(uuid, parsed)
        return parsed
    }

    fun save(uuid: UUID, state: RuleState) {
        val file = stateFile(uuid)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(RuleState.serializer(), state))
    }

    fun readStateJson(uuid: UUID, configText: String): String {
        val state = load(uuid, configText)
        return json.encodeToString(RuleState.serializer(), state)
    }

    fun parseStateJson(stateJson: String): RuleState {
        return json.decodeFromString(RuleState.serializer(), stateJson)
    }

    private fun stateFile(uuid: UUID): File {
        return File(context.importedDir, "$uuid/rules_state.json")
    }

    private fun syncProviderRules(stored: RuleState, incoming: RuleState): RuleState {
        val byKey = stored.rules.associateBy {
            "${it.type.uppercase()},${it.value.uppercase()},${it.policy.uppercase()}"
        }
        val mergedRules = incoming.rules.mapIndexed { index, rule ->
            val key = "${rule.type.uppercase()},${rule.value.uppercase()},${rule.policy.uppercase()}"
            val old = byKey[key]
            if (old != null) {
                rule.copy(
                    id = old.id,
                    enabled = old.enabled,
                    deleted = old.deleted,
                    isRestorable = old.isRestorable || rule.isRestorable,
                    order = index,
                )
            } else {
                rule.copy(order = index)
            }
        }.toMutableList()

        // Keep non-active rules that are absent from current YAML parse:
        // - disabled rules (so toggled OFF can be re-enabled later)
        // - deleted provider rules (restore support)
        // - manual rules that user keeps locally
        val incomingKeys = incoming.rules.map {
            "${it.type.uppercase()},${it.value.uppercase()},${it.policy.uppercase()}"
        }.toSet()
        val retained = stored.rules.filter { rule ->
            val k = "${rule.type.uppercase()},${rule.value.uppercase()},${rule.policy.uppercase()}"
            k !in incomingKeys &&
                (
                    rule.deleted ||
                        !rule.enabled ||
                        rule.source == RuleSource.MANUAL
                    )
        }
        retained.forEach { mergedRules.add(it.copy(order = mergedRules.size)) }
        return stored.copy(
            providers = incoming.providers,
            rules = mergedRules,
        )
    }
}
