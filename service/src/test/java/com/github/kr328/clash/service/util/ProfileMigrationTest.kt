package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileMigrationTest {
    private val dir: File = Files.createTempDirectory("profile-migration").toFile()
    private val store = UserLayerStore(dir)
    private val uuid: UUID = UUID.randomUUID()
    private val json = Json { ignoreUnknownKeys = true }

    @AfterTest fun cleanup() {
        dir.deleteRecursively()
    }

    private val legacyConfig = """
        mixed-port: 7890
        proxies:
          - name: US-1
            type: ss
            server: 1.2.3.4
            port: 8388
            cipher: aes-128-gcm
            password: p
            dialer-proxy: JP-2
        dns:
          enable: true
        rules:
          - MATCH,DIRECT
    """.trimIndent() + "\n"

    private val fakeSnapshot: (File) -> ProfileSnapshot? = {
        ProfileSnapshot(dns = buildJsonObject { put("enable", JsonPrimitive(true)) })
    }

    @Test fun migrates_legacy_profile_to_overlay() {
        val profileDir = File(dir, uuid.toString()).apply { mkdirs() }
        File(profileDir, ProfileComposer.CONFIG_FILE).writeText(legacyConfig)
        val rulesJson = json.encodeToString(
            RuleState.serializer(),
            RuleState(rules = listOf(RuleItem(id = "r1", type = "DOMAIN", value = "mine.test", policy = "DIRECT"))),
        )

        val migrated = ProfileMigration.migrateIfNeeded(
            profileDir, uuid, store,
            rulesStateJson = rulesJson,
            dnsHostsManaged = true,
            tunnelsManaged = false,
            parseSnapshot = fakeSnapshot,
        )
        assertTrue(migrated)

        // subscription.yaml is the current config.yaml; config.yaml is untouched.
        assertEquals(legacyConfig, File(profileDir, ProfileComposer.SUBSCRIPTION_FILE).readText())
        assertEquals(legacyConfig, File(profileDir, ProfileComposer.CONFIG_FILE).readText())

        // Edits extracted into the layer.
        val layer = store.load(uuid)
        assertEquals(1, layer.rules.rules.size)
        assertEquals(mapOf("US-1" to "JP-2"), layer.proxyChain)
        assertTrue(layer.dnsHosts?.enable == true, "managed dns extracted")
    }

    @Test fun migration_is_idempotent() {
        val profileDir = File(dir, uuid.toString()).apply { mkdirs() }
        File(profileDir, ProfileComposer.CONFIG_FILE).writeText(legacyConfig)

        assertTrue(ProfileMigration.migrateIfNeeded(profileDir, uuid, store, null, false, false, fakeSnapshot))
        assertTrue(ProfileMigration.isMigrated(profileDir))
        // Second pass: subscription.yaml already exists → no-op.
        assertFalse(ProfileMigration.migrateIfNeeded(profileDir, uuid, store, null, false, false, fakeSnapshot))
    }
}
