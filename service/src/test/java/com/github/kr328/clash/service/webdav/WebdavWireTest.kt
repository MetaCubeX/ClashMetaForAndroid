package com.github.kr328.clash.service.webdav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class WebdavWireTest {
    @Test
    fun unknownProfileAndFileFieldsSurviveAnEditAndStateReload() {
        val body = """{"kind":"$WEBDAV_PROFILE_KIND","version":1,"uuid":"a","name":"old","type":"File","future":{"enabled":true},"files":[{"path":"config.yaml","contentBase64":"YQ==","futureFile":17}]}"""
        val parsed = parseProfile(body)!!
        val state = LocalState(remoteProfiles = mapOf("profiles/a.json" to parsed))
        val disk = webdavJson.encodeToString(LocalState.serializer(), state)
        val restored = webdavJson.decodeFromString(LocalState.serializer(), disk)
        val output = Json.parseToJsonElement(encodeProfile(restored.remoteProfiles.getValue("profiles/a.json").copy(name = "new"))).jsonObject
        assertEquals("\"new\"", output["name"].toString())
        assertEquals("{\"enabled\":true}", output["future"].toString())
        assertEquals("17", output["files"]!!.jsonArray[0].jsonObject["futureFile"].toString())
        assertFalse(output.containsKey("_syncPreserved"))
    }

    @Test
    fun aConflictUsesFreshUnknownFieldsAndKnownContentWinner() {
        val old = parseProfile("""{"kind":"$WEBDAV_PROFILE_KIND","version":1,"uuid":"a","name":"local","type":"File","syncRev":5,"future":"old"}""")!!
        val fresh = parseProfile("""{"kind":"$WEBDAV_PROFILE_KIND","version":1,"uuid":"a","name":"remote","type":"File","syncRev":2,"future":"new"}""")!!
        val merged = mergeProfile(old, fresh)
        val output = Json.parseToJsonElement(encodeProfile(merged)).jsonObject
        assertEquals("\"local\"", output["name"].toString())
        assertEquals("\"new\"", output["future"].toString())
    }

    @Test
    fun unknownNestedMetaFieldsSurviveButDoNotCauseRepeatedPublication() {
        val raw = """{"kind":"$WEBDAV_SYNC_KIND","version":1,"future":2,"appsStrategies":[{"uuid":"s","name":"strategy","futurePolicy":3}]}"""
        val meta = parseMeta(raw)
        val output = Json.parseToJsonElement(encodeMeta(meta.copy(updatedAt = 10))).jsonObject
        assertEquals("2", output["future"].toString())
        assertEquals("3", output["appsStrategies"]!!.jsonArray[0].jsonObject["futurePolicy"].toString())
        assertTrue(sameSyncMeta(meta, meta.copy(preserved = null, updatedAt = 100)))
    }

    @Test
    fun routeChangesShareStateButAccountAndFolderChangesDoNot() {
        val first = StoredWebdavSettings(url = "https://example.com/dav/", lanUrl = "http://192.168.1.2/dav", username = "user")
        assertEquals(webdavStateScope(first), webdavStateScope(first.copy(lanUrl = "http://192.168.1.3/dav")))
        assertNotEquals(webdavStateScope(first), webdavStateScope(first.copy(username = "other")))
        assertNotEquals(webdavStateScope(first), webdavStateScope(first.copy(remotePath = "/other")))
        assertNotEquals(webdavStateScope(first), webdavStateScope(first.copy(url = "https://elsewhere.example/dav")))
    }

    @Test
    fun ipcConnectionDoesNotRequireInternalSealedPasswordOrAutoFlag() {
        val ipc = StoredWebdavSettings(url = "https://example.com/dav", username = "user", password = "secret")
        val stored = ipc.copy(passwordSealed = "encrypted", autoSync = true)
        assertTrue(sameWebdavConnection(ipc, stored))
        assertFalse(sameWebdavConnection(ipc, stored.copy(password = "new")))
        assertFalse(sameWebdavConnection(ipc, stored.copy(remotePath = "/another")))
        assertFalse(sameWebdavConnection(ipc, stored.copy(username = "other")))
    }

    @Test(expected = WebdavException::class)
    fun anEmptyObjectCannotResetRemoteMetadata() {
        parseMeta("{}")
    }

    @Test
    fun exportRefusesProviderSymlinksAndDirectoryLoops() {
        val root = Files.createTempDirectory("webdav-paths").toFile()
        try {
            val profile = root.resolve("profile").apply { mkdirs() }
            val outside = root.resolve("outside").apply { writeText("private") }
            val providers = profile.resolve("providers").apply { mkdirs() }
            val link = providers.resolve("foreign.yaml").toPath()
            Files.createSymbolicLink(link, outside.toPath())
            try {
                readFiles(profile)
                fail("a linked provider must not be read")
            } catch (error: WebdavException) {
                assertEquals("bad_path", error.code)
            }
            Files.delete(link)
            val loop = providers.resolve("loop").toPath()
            Files.createSymbolicLink(loop, providers.toPath())
            try {
                readFiles(profile)
                fail("a linked directory must not be traversed")
            } catch (error: WebdavException) {
                assertEquals("bad_path", error.code)
            }
            Files.delete(loop)
        } finally {
            root.deleteRecursively()
        }
    }
}
