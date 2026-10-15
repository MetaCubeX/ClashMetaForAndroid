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
        assertEquals("/sync/Configs/clash-meta", resolveWebdavRemoteFolder(""))
        assertEquals(WEBDAV_DEFAULT_URL, normalizeWebdavUrl(""))
        assertEquals(WEBDAV_DEFAULT_URL, normalizeWebdavUrl(WEBDAV_LEGACY_MISREAD_URL))
        assertEquals(WEBDAV_DEFAULT_REMOTE_PATH, normalizeWebdavRemotePath("/clash-meta"))
        assertEquals(WEBDAV_DEFAULT_REMOTE_PATH, normalizeWebdavRemotePath("/sync/Configs/cursor-manager"))
        assertEquals("", normalizeWebdavLanUrl(""))
        assertEquals("", normalizeWebdavLanUrl(null))
        assertEquals("", normalizeWebdavLanUrl("  "))
        assertEquals("http://192.168.1.8:5244/dav", normalizeWebdavLanUrl("http://192.168.1.8:5244/dav/"))
        assertEquals("", seedWebdavSettings(StoredWebdavSettings(password = "x", autoSync = true, lanUrl = "")).lanUrl)
        assertEquals(WEBDAV_DEFAULT_LAN_URL, seedWebdavSettings(StoredWebdavSettings()).lanUrl)
    }

    @Test
    fun lanHttpIsPrivateOnly() {
        assertEquals(
            "http://192.168.1.69:5244/dav/sync/Configs/clash-meta/profiles/a.json",
            joinWebdavUrl("http://192.168.1.69:5244/dav", "/sync/Configs/clash-meta/profiles/a.json", allowPrivateHttp = true),
        )
        try {
            joinWebdavUrl("http://192.168.1.69:5244/dav", "/sync/Configs/clash-meta/a.json")
            throw AssertionError("lan http needs the private flag")
        } catch (err: WebdavException) {
            assertEquals("https", err.code)
        }
        try {
            joinWebdavUrl("http://example.com/dav", "/sync/Configs/clash-meta/a.json", allowPrivateHttp = true)
            throw AssertionError("public http is rejected")
        } catch (err: WebdavException) {
            assertEquals("lan_http", err.code)
        }
    }

    @Test
    fun lanProbeFallsBackToPublicAddress() {
        val folder = "http://192.168.1.69:5244/dav/sync/Configs/clash-meta/"
        assertEquals(
            "http://192.168.1.69:5244/dav",
            resolveWebdavBaseUrl(
                url = WEBDAV_DEFAULT_URL,
                lanUrl = WEBDAV_DEFAULT_LAN_URL,
                remotePath = WEBDAV_DEFAULT_REMOTE_PATH,
                allowLan = true,
                probe = { it == folder },
            ),
        )
        assertEquals(
            WEBDAV_DEFAULT_URL,
            resolveWebdavBaseUrl(
                url = WEBDAV_DEFAULT_URL,
                lanUrl = WEBDAV_DEFAULT_LAN_URL,
                remotePath = WEBDAV_DEFAULT_REMOTE_PATH,
                allowLan = true,
                probe = { false },
            ),
        )
        assertEquals(
            WEBDAV_DEFAULT_URL,
            resolveWebdavBaseUrl(
                url = WEBDAV_DEFAULT_URL,
                lanUrl = WEBDAV_DEFAULT_LAN_URL,
                remotePath = WEBDAV_DEFAULT_REMOTE_PATH,
                allowLan = false,
                probe = { true },
            ),
        )
        try {
            resolveWebdavBaseUrl(
                url = "",
                lanUrl = WEBDAV_DEFAULT_LAN_URL,
                remotePath = WEBDAV_DEFAULT_REMOTE_PATH,
                allowLan = false,
                probe = { true },
            )
            throw AssertionError("wifi is required when only the lan address is set")
        } catch (err: WebdavException) {
            assertEquals("lan_offline", err.code)
        }
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
    fun newerAppGroupReplacesPackagesWithoutRemovingTheOtherGroup() {
        val local = AppsStrategyPayload("a", "Home", "AcceptSelected", listOf("com.a"), 10)
        val other = AppsStrategyPayload("b", "Work", "DenySelected", listOf("com.b"), 10)
        val remote = AppsStrategyPayload("a", "Home", "AcceptSelected", listOf("com.a", "com.c"), 20)
        val merged = mergeStrategies(listOf(local, other), listOf(remote), emptyList(), emptyList(), 100)
        assertEquals(setOf("a", "b"), merged.strategies.map { it.uuid }.toSet())
        assertEquals(listOf("com.a", "com.c"), merged.strategies.first { it.uuid == "a" }.packages)
    }

    @Test
    fun remotePathCannotLeaveTheWebdavOrigin() {
        assertEquals(
            "https://example.com/dav/My%20Configs/a.json",
            joinWebdavUrl("https://example.com/dav", "/My Configs/a.json"),
        )
        for (path in listOf("/../etc/passwd", "http://evil.example/x", "//evil.example/x")) {
            try {
                joinWebdavUrl("https://example.com/dav", path)
                throw AssertionError(path)
            } catch (err: WebdavException) {
                assertEquals(path, "bad_path", err.code)
            }
        }
    }

    @Test
    fun brokenLanTargetFallsBackAndPublicHttpIsRejected() {
        var probed = false
        assertEquals(
            WEBDAV_DEFAULT_URL,
            resolveWebdavBaseUrl(
                url = WEBDAV_DEFAULT_URL,
                lanUrl = WEBDAV_DEFAULT_LAN_URL,
                remotePath = "/../escape",
                allowLan = true,
                probe = { probed = true; true },
            ),
        )
        assertFalse(probed)
        val encoded = mutableListOf<String>()
        assertEquals(
            WEBDAV_DEFAULT_LAN_URL,
            resolveWebdavBaseUrl(
                url = WEBDAV_DEFAULT_URL,
                lanUrl = WEBDAV_DEFAULT_LAN_URL,
                remotePath = "/My Configs",
                allowLan = true,
                probe = { encoded += it; true },
            ),
        )
        assertEquals(listOf("http://192.168.1.69:5244/dav/My%20Configs/"), encoded)
        try {
            resolveWebdavBaseUrl(
                url = "http://10.0.0.8/dav",
                lanUrl = "",
                remotePath = WEBDAV_DEFAULT_REMOTE_PATH,
                allowLan = false,
                probe = { true },
            )
            throw AssertionError("public http")
        } catch (err: WebdavException) {
            assertEquals("https", err.code)
        }
    }

    @Test
    fun sameSideDuplicateKeepsTheNewerProfile() {
        val older = profile("aaa", source = "https://example.com/sub", updatedAt = 10, name = "old")
        val newer = profile("zzz", source = "https://example.com/sub", updatedAt = 40, name = "new")
        val merged = mergeWebdav(
            listOf(older, newer),
            emptyList(),
            emptyList(),
            emptyList(),
            access(),
            access(),
            now = 100,
        )
        assertEquals("zzz", merged.profiles.single().uuid)
        assertEquals("new", merged.profiles.single().name)
        val tied = mergeWebdav(
            listOf(
                profile("aaa", source = "https://example.com/sub", updatedAt = 40),
                profile("mmm", source = "https://example.com/sub", updatedAt = 40),
            ),
            emptyList(),
            emptyList(),
            emptyList(),
            access(),
            access(),
            now = 100,
        )
        assertEquals("mmm", tied.profiles.single().uuid)
    }

    @Test
    fun sameSideDuplicateStrategyKeepsTheNewerCopy() {
        val older = AppsStrategyPayload("a", "Old", "AcceptAll", emptyList(), 10)
        val newer = AppsStrategyPayload("a", "New", "AcceptSelected", listOf("com.a"), 30)
        val merged = mergeStrategies(listOf(older, newer), emptyList(), emptyList(), emptyList(), 100)
        assertEquals("New", merged.strategies.single().name)
        assertEquals(listOf("com.a"), merged.strategies.single().packages)
    }

    @Test
    fun droppedTombstoneIsNotWrittenBack() {
        val stale = TombstonePayload("id", "https://example.com/sub", "Url", 100)
        val fresh = TombstonePayload("other", "", "File", 900)
        val stored = retainMergedTombstones(
            merged = emptyList(),
            snapshot = listOf(stale),
            current = listOf(stale, fresh),
            now = 1_000,
        )
        assertEquals(listOf(fresh), stored)
    }

    @Test
    fun emptyMetaIsBrokenAndExistingMetaKeepsItsEtag() {
        try {
            interpretRemoteMeta(found = true, body = "  ", getEtag = "\"abc\"", listedEtag = null)
            throw AssertionError("empty meta")
        } catch (err: WebdavException) {
            assertEquals("broken_meta", err.code)
        }
        val body = encodeMeta(SyncMetaPayload())
        val fromList = interpretRemoteMeta(found = true, body = body, getEtag = null, listedEtag = "\"from-list\"")
        assertTrue(fromList.recordEtag)
        assertEquals("\"from-list\"", fromList.etag)
        val missing = interpretRemoteMeta(found = false, body = "", getEtag = null, listedEtag = "\"x\"")
        assertFalse(missing.recordEtag)
        assertTrue(webdavEtagsMatch("W/\"abc\"", "\"abc\""))
        assertFalse(webdavEtagsMatch("\"abc\"", "\"def\""))
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
