package com.github.kr328.clash.service.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebdavSyncTest {
    @Test
    fun httpsIsRequired() {
        try {
            joinWebdavUrl("http://example.com/dav", "/clash-meta/a.json")
            throw AssertionError("http should be rejected")
        } catch (err: WebdavException) {
            assertEquals("https", err.code)
        }
    }

    @Test
    fun joinsRemoteFolder() {
        assertEquals(
            "https://example.com/dav/clash-meta/profiles/a.json",
            fileUrl("https://example.com/dav", "/clash-meta", "profiles/a.json"),
        )
        assertEquals("/clash-meta", resolveWebdavRemoteFolder("/clash-meta/accounts.json"))
        assertEquals("/clash-meta", resolveWebdavRemoteFolder(""))
    }

    @Test
    fun parsesPropfindAndSkipsItself() {
        val xml = """
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/clash-meta/</D:href>
                <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/clash-meta/profiles/</D:href>
                <D:propstat><D:prop>
                  <D:resourcetype><D:collection/></D:resourcetype>
                  <D:getlastmodified>Thu, 18 Sep 2026 00:00:00 GMT</D:getlastmodified>
                </D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/clash-meta/_sync.json</D:href>
                <D:propstat><D:prop><D:getetag>"abc"</D:getetag></D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()
        val entries = parsePropfindEntries(xml, "https://example.com/dav/clash-meta/")
        assertEquals(listOf("profiles", "_sync.json"), entries.map { it.name })
        assertEquals("\"abc\"", entries[1].etag)
        assertTrue(entries[0].collection)
        assertFalse(entries[1].collection)
    }

    @Test
    fun staleTempNeedsADate() {
        assertFalse(isStaleWebdavTemp("a.json.abcd1234.tmp", null, 10_000))
        val old = "Thu, 01 Jan 2026 00:00:00 GMT"
        assertTrue(isStaleWebdavTemp("a.json.abcd1234.tmp", old, parseHttpDate(old)!! + WEBDAV_TMP_MAX_AGE_MS))
        assertFalse(isStaleWebdavTemp("a.json", old, Long.MAX_VALUE))
    }

    @Test
    fun keepsProfilesThatExistOnOnlyOneSide() {
        val local = profile("local-only", source = "https://local.example/sub")
        val remote = profile("remote-only", source = "https://remote.example/sub")
        val merged = mergeWebdav(
            localProfiles = listOf(local),
            remoteProfiles = listOf(remote),
            localTombstones = emptyList(),
            remoteTombstones = emptyList(),
            localAccess = access(),
            remoteAccess = access(),
            now = 1_000,
        )
        assertEquals(setOf("local-only", "remote-only"), merged.profiles.map { it.uuid }.toSet())
    }

    @Test
    fun sameSubscriptionKeepsLocalUuidAndNewerConfig() {
        val local = profile(
            uuid = "local-id",
            source = "https://example.com/sub/",
            updatedAt = 100,
            name = "Local name",
        )
        val remote = profile(
            uuid = "remote-id",
            source = "https://example.com/sub",
            updatedAt = 200,
            name = "Remote name",
        )
        val merged = mergeWebdav(
            listOf(local),
            listOf(remote),
            emptyList(),
            emptyList(),
            access(),
            access(),
            now = 1_000,
        )
        assertEquals(1, merged.profiles.size)
        val profile = merged.profiles.single()
        assertEquals("local-id", profile.uuid)
        assertEquals("Remote name", profile.name)
        assertEquals(200, profile.updatedAt)
    }

    @Test
    fun tombstoneRemovesMatchingSubscriptionOnEitherUuid() {
        val local = profile("local-id", source = "https://example.com/sub", updatedAt = 100)
        val tombstone = TombstonePayload(
            uuid = "other-id",
            source = "https://example.com/sub",
            type = "Url",
            deletedAt = 150,
        )
        val merged = mergeWebdav(
            listOf(local),
            emptyList(),
            listOf(tombstone),
            emptyList(),
            access(),
            access(),
            now = 1_000,
        )
        assertTrue(merged.profiles.isEmpty())
        assertEquals(1, merged.tombstones.size)
    }

    @Test
    fun newerProfileDropsTombstone() {
        val remote = profile("id", source = "https://example.com/sub", updatedAt = 500)
        val tombstone = TombstonePayload("id", "https://example.com/sub", "Url", 100)
        val merged = mergeWebdav(
            emptyList(),
            listOf(remote),
            emptyList(),
            listOf(tombstone),
            access(),
            access(),
            now = 1_000,
        )
        assertEquals("id", merged.profiles.single().uuid)
        assertTrue(merged.tombstones.isEmpty())
    }

    @Test
    fun expiredTombstoneIsDropped() {
        val tombstone = TombstonePayload("id", "", "File", 1)
        val merged = mergeWebdav(
            listOf(profile("id", type = "File", source = "", updatedAt = 0)),
            emptyList(),
            listOf(tombstone),
            emptyList(),
            access(),
            access(),
            now = tombstone.deletedAt + WEBDAV_TOMBSTONE_TTL_MS + 1,
        )
        assertEquals("id", merged.profiles.single().uuid)
        assertTrue(merged.tombstones.isEmpty())
    }

    @Test
    fun newerAccessControlWins() {
        val merged = mergeWebdav(
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            access(updatedAt = 10, packages = listOf("com.local")),
            access(mode = "AcceptSelected", updatedAt = 20, packages = listOf("com.remote")),
            now = 100,
        )
        assertEquals("AcceptSelected", merged.access.mode)
        assertEquals(listOf("com.remote"), merged.access.packages)
    }

    @Test
    fun rejectsUnsafePathsAndBrokenProfiles() {
        try {
            safeRelativePath("../config.yaml")
            throw AssertionError("path should be rejected")
        } catch (err: WebdavException) {
            assertEquals("bad_path", err.code)
        }
        assertNull(parseProfile("""{"kind":"other","version":1,"uuid":"a","name":"n"}"""))
        val encoded = encodeProfile(profile("abc"))
        assertEquals("abc", parseProfile(encoded)?.uuid)
    }

    private fun profile(
        uuid: String,
        source: String = "https://example.com/$uuid",
        type: String = "Url",
        name: String = uuid,
        updatedAt: Long = 1,
    ): ProfilePayload {
        return ProfilePayload(
            uuid = uuid,
            name = name,
            type = type,
            source = source,
            updatedAt = updatedAt,
            files = listOf(ProfileFilePayload("config.yaml", "e30=")),
        )
    }

    private fun access(
        mode: String = "AcceptAll",
        updatedAt: Long = 0,
        packages: List<String> = emptyList(),
    ): AccessControlPayload {
        return AccessControlPayload(mode, packages, updatedAt)
    }
}
