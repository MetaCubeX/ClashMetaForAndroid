package com.github.kr328.clash.service.webdav

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebdavSyncTest {
    private val publicUrl = "https://example.com/dav"
    private val lanUrl = "http://192.168.1.8:5244/dav"
    private val remotePath = "/clash-meta"

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
        assertEquals(WEBDAV_DEFAULT_REMOTE_PATH, resolveWebdavRemoteFolder(""))
        assertEquals("", normalizeWebdavUrl(""))
        assertEquals(WEBDAV_DEFAULT_REMOTE_PATH, normalizeWebdavRemotePath("/clash-meta"))
        assertEquals("", normalizeWebdavLanUrl(""))
        assertEquals("", normalizeWebdavLanUrl(null))
        assertEquals("", normalizeWebdavLanUrl("  "))
        assertEquals("http://192.168.1.8:5244/dav", normalizeWebdavLanUrl("http://192.168.1.8:5244/dav/"))
        assertEquals("", seedWebdavSettings(StoredWebdavSettings(password = "x", autoSync = true, lanUrl = "")).lanUrl)
        assertEquals("", seedWebdavSettings(StoredWebdavSettings()).lanUrl)
    }

    @Test
    fun unrelatedWriteKeepsSealedPassword() {
        val unreadable = StoredWebdavSettings(
            password = "",
            passwordSealed = "v1:broken",
            passwordUnreadable = true,
        )
        assertEquals("v1:broken", sealedForWrite(unreadable.copy(url = "https://example.com/dav"), unreadable, false))
        assertEquals("v1:broken", sealedForWrite(unreadable, unreadable, false))
        assertEquals("", sealedForWrite(unreadable.copy(password = ""), unreadable, true))
    }

    @Test
    fun unreadablePasswordKeepsEndpoint() {
        val seeded = seedWebdavSettings(
            StoredWebdavSettings(
                url = "https://example.com/dav/",
                username = "tester",
                passwordSealed = "v1:broken",
                passwordUnreadable = true,
                remotePath = "/clash-meta/",
            ),
        )
        assertEquals("https://example.com/dav", seeded.url)
        assertEquals("tester", seeded.username)
        assertEquals("/clash-meta", seeded.remotePath)
        assertEquals("v1:broken", seeded.passwordSealed)
    }

    @Test
    fun missingAndUnreadablePasswordDiffer() {
        assertEquals("", WebdavCipher.open(""))
        assertNull(WebdavCipher.open("v1:broken"))
    }

    @Test
    fun touchedPasswordIsResealed() {
        val previous = StoredWebdavSettings(password = "old", passwordSealed = "v1:old")
        val next = sealedForWrite(previous.copy(password = "new"), previous, true)
        assertTrue(next.isNotEmpty())
        assertFalse(next == previous.passwordSealed)
    }

    @Test
    fun lanHttpIsPrivateOnly() {
        assertEquals(
            "http://192.168.1.8:5244/dav/clash-meta/profiles/a.json",
            joinWebdavUrl("http://192.168.1.8:5244/dav", "/clash-meta/profiles/a.json", allowPrivateHttp = true),
        )
        try {
            joinWebdavUrl("http://192.168.1.8:5244/dav", "/clash-meta/a.json")
            throw AssertionError("lan http needs the private flag")
        } catch (err: WebdavException) {
            assertEquals("https", err.code)
        }
        try {
            joinWebdavUrl("http://example.com/dav", "/clash-meta/a.json", allowPrivateHttp = true)
            throw AssertionError("public http is rejected")
        } catch (err: WebdavException) {
            assertEquals("lan_http", err.code)
        }
    }

    @Test
    fun lanProbeFallsBackToPublicAddress() {
        val folder = "$lanUrl$remotePath/"
        assertEquals(
            lanUrl,
            resolveWebdavBaseUrl(
                url = publicUrl,
                lanUrl = lanUrl,
                remotePath = remotePath,
                allowLan = true,
                probe = { it == folder },
            ),
        )
        assertEquals(
            publicUrl,
            resolveWebdavBaseUrl(
                url = publicUrl,
                lanUrl = lanUrl,
                remotePath = remotePath,
                allowLan = true,
                probe = { false },
            ),
        )
        assertEquals(
            publicUrl,
            resolveWebdavBaseUrl(
                url = publicUrl,
                lanUrl = lanUrl,
                remotePath = remotePath,
                allowLan = false,
                probe = { true },
            ),
        )
        try {
            resolveWebdavBaseUrl(
                url = "",
                lanUrl = lanUrl,
                remotePath = remotePath,
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
            publicUrl,
            resolveWebdavBaseUrl(
                url = publicUrl,
                lanUrl = lanUrl,
                remotePath = "/../escape",
                allowLan = true,
                probe = { probed = true; true },
            ),
        )
        assertFalse(probed)
        val encoded = mutableListOf<String>()
        assertEquals(
            lanUrl,
            resolveWebdavBaseUrl(
                url = publicUrl,
                lanUrl = lanUrl,
                remotePath = "/My Configs",
                allowLan = true,
                probe = { encoded += it; true },
            ),
        )
        assertEquals(listOf("$lanUrl/My%20Configs/"), encoded)
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

    @Test
    fun equalTimestampsPickTheSameProfileFromEitherSide() {
        val left = profile("local-id", source = "https://example.com/sub", name = "Alpha", updatedAt = 50)
        val right = profile("remote-id", source = "https://example.com/sub", name = "Beta", updatedAt = 50)
        val first = mergeWebdav(listOf(left), listOf(right), emptyList(), emptyList(), access(), access(), 1_000)
        val second = mergeWebdav(listOf(right), listOf(left), emptyList(), emptyList(), access(), access(), 1_000)
        assertEquals(first.profiles.single().name, second.profiles.single().name)
    }

    @Test
    fun higherSyncRevWinsOverANewerClock() {
        val slow = profile("local-id", source = "https://example.com/sub", name = "Slow", updatedAt = 10, syncRev = 3)
        val fast = profile("remote-id", source = "https://example.com/sub", name = "Fast", updatedAt = 900, syncRev = 1)
        val merged = mergeWebdav(listOf(slow), listOf(fast), emptyList(), emptyList(), access(), access(), 1_000)
        assertEquals("Slow", merged.profiles.single().name)
        assertEquals("local-id", merged.profiles.single().uuid)
    }

    @Test
    fun newerUsageDoesNotReplaceTheConfigurationBody() {
        val local = profile(
            "local-id",
            source = "https://example.com/sub",
            name = "Local",
            updatedAt = 10,
            syncRev = 1,
            upload = 1,
        )
        val remote = profile(
            "remote-id",
            source = "https://example.com/sub",
            name = "Remote",
            updatedAt = 20,
            syncRev = 5,
            upload = 2,
        )
        val usage = ProfileUsagePayload(uuid = "local-id", upload = 80, updatedAt = 900, writerId = "phone")
        val merged = mergeWebdav(
            listOf(local),
            listOf(remote),
            emptyList(),
            emptyList(),
            access(),
            access(),
            1_000,
            localUsage = listOf(usage),
        )
        assertEquals("Remote", merged.profiles.single().name)
        assertEquals(80, merged.profiles.single().upload)
        assertEquals(80, merged.profileUsage.single().upload)
    }

    @Test
    fun explicitUsageBeatsStaleCountersInANewerProfileFile() {
        val remote = profile(
            "remote-id",
            source = "https://example.com/sub",
            name = "Remote",
            updatedAt = 900,
            syncRev = 5,
            upload = 2,
        )
        val usage = ProfileUsagePayload(uuid = "remote-id", upload = 80, updatedAt = 300, writerId = "phone")
        val merged = mergeWebdav(
            emptyList(),
            listOf(remote),
            emptyList(),
            emptyList(),
            access(),
            access(),
            1_000,
            remoteUsage = listOf(usage),
        )
        assertEquals("Remote", merged.profiles.single().name)
        assertEquals(80, merged.profiles.single().upload)
        assertEquals(80, merged.profileUsage.single().upload)
    }

    @Test
    fun skippedProfileBodyStillMergesRemoteUsage() {
        assertTrue(shouldSkipProfileGet("W/\"same\"", "\"same\"", "fp", presentLocally = true))
        val local = profile("id", name = "Local", updatedAt = 10, upload = 1, download = 2).copy(total = 3, expire = 4)
        val localUsage = ProfileUsagePayload(
            uuid = "id",
            upload = 1,
            download = 2,
            total = 3,
            expire = 4,
            updatedAt = 10,
            writerId = "phone",
        )
        val newer = ProfileUsagePayload(
            uuid = "id",
            upload = 11,
            download = 22,
            total = 33,
            expire = 44,
            updatedAt = 90,
            writerId = "other",
        )
        val merged = mergeWebdav(
            localProfiles = listOf(local),
            remoteProfiles = emptyList(),
            localTombstones = emptyList(),
            remoteTombstones = emptyList(),
            localAccess = access(),
            remoteAccess = access(),
            now = 1_000,
            localUsage = listOf(localUsage),
            remoteUsage = listOf(newer),
        )
        assertEquals("Local", merged.profiles.single().name)
        assertEquals(11, merged.profiles.single().upload)
        assertEquals(22, merged.profiles.single().download)
        assertEquals(33, merged.profiles.single().total)
        assertEquals(44, merged.profiles.single().expire)
        assertEquals(newer, merged.profileUsage.single())
        assertEquals(listOf(newer), metaFrom(merged, legacyAccess = access()).profileUsage)

        val older = newer.copy(upload = 7, download = 8, total = 9, expire = 6, updatedAt = 5)
        val kept = mergeWebdav(
            localProfiles = listOf(local),
            remoteProfiles = emptyList(),
            localTombstones = emptyList(),
            remoteTombstones = emptyList(),
            localAccess = access(),
            remoteAccess = access(),
            now = 1_000,
            localUsage = listOf(localUsage),
            remoteUsage = listOf(older),
        )
        assertEquals(1, kept.profiles.single().upload)
        assertEquals(2, kept.profiles.single().download)
        assertEquals(3, kept.profiles.single().total)
        assertEquals(4, kept.profiles.single().expire)
        assertEquals(localUsage, kept.profileUsage.single())
        assertEquals(listOf(localUsage), metaFrom(kept, legacyAccess = access()).profileUsage)

        val otherUuid = newer.copy(uuid = "someone-else")
        val ignored = mergeWebdav(
            localProfiles = listOf(local),
            remoteProfiles = emptyList(),
            localTombstones = emptyList(),
            remoteTombstones = emptyList(),
            localAccess = access(),
            remoteAccess = access(),
            now = 1_000,
            localUsage = listOf(localUsage),
            remoteUsage = listOf(otherUuid),
        )
        assertEquals(localUsage, ignored.profileUsage.single())
    }

    @Test
    fun usageCountersStayOutOfTheUploadFingerprint() {
        val left = profile("id", upload = 1, download = 2)
        val right = left.copy(upload = 9, download = 8, total = 3, expire = 4)
        assertEquals(profileContentHash(left), profileContentHash(right))
        assertEquals(profileBodyFingerprint(left), profileBodyFingerprint(right))
        assertFalse(profileBodyFingerprint(left) == profileBodyFingerprint(left.copy(syncRev = 1)))
    }

    @Test
    fun higherProfileRevisionDropsAnOlderTombstone() {
        val remote = profile("id", source = "https://example.com/sub", name = "kept", updatedAt = 10, syncRev = 4)
        val tombstone = TombstonePayload("id", "https://example.com/sub", "Url", deletedAt = 9_000, rev = 1)
        val merged = mergeWebdav(emptyList(), listOf(remote), emptyList(), listOf(tombstone), access(), access(), 10_000)
        assertEquals("kept", merged.profiles.single().name)
        assertTrue(merged.tombstones.isEmpty())
    }

    @Test
    fun higherTombstoneRevisionDeletesANewerClock() {
        val remote = profile("id", source = "https://example.com/sub", name = "gone", updatedAt = 500, syncRev = 1)
        val tombstone = TombstonePayload("id", "https://example.com/sub", "Url", deletedAt = 10, rev = 3)
        val merged = mergeWebdav(emptyList(), listOf(remote), emptyList(), listOf(tombstone), access(), access(), 1_000)
        assertTrue(merged.profiles.isEmpty())
        assertEquals(1, merged.tombstones.size)
    }

    @Test
    fun equalAccessClocksDoNotPreferTheLocalCopy() {
        val local = access(mode = "AcceptAll", updatedAt = 10, packages = listOf("a"), writerId = "aaa")
        val remote = access(mode = "AcceptSelected", updatedAt = 10, packages = listOf("b"), writerId = "bbb")
        val first = mergeWebdav(emptyList(), emptyList(), emptyList(), emptyList(), local, remote, 100)
        val second = mergeWebdav(emptyList(), emptyList(), emptyList(), emptyList(), remote, local, 100)
        assertEquals(first.access.mode, second.access.mode)
        assertEquals("AcceptSelected", first.access.mode)
    }

    @Test
    fun blankProfileBodyIsUnreadableAndNotDeleted() {
        assertTrue(readProfileBody("  ") is ProfileBodyRead.Unreadable)
        assertTrue(readProfileBody("{") is ProfileBodyRead.Unreadable)
        val name = profileFileName("abc")
        assertFalse(isDeletableRemoteProfile(name, kept = emptySet(), unreadable = setOf(name)))
        assertTrue(isDeletableRemoteProfile(name, kept = emptySet(), unreadable = emptySet()))
    }

    @Test
    fun unchangedEtagSkipsTheDownloadOnlyWhenTheLocalCopyIsKnown() {
        assertTrue(shouldSkipProfileGet("W/\"abc\"", "\"abc\"", "fp", presentLocally = true))
        assertFalse(shouldSkipProfileGet(null, "\"abc\"", "fp", presentLocally = true))
        assertFalse(shouldSkipProfileGet("\"abc\"", "\"abc\"", null, presentLocally = true))
        assertFalse(shouldSkipProfileGet("\"abc\"", "\"abc\"", "fp", presentLocally = false))
        assertFalse(shouldSkipProfileGet("\"abc\"", "\"def\"", "fp", presentLocally = true))
    }

    @Test
    fun lanRouteIsReusedUntilTheMemoExpires() {
        val memo = WebdavRouteMemo("k", "http://lan", 1_000)
        assertEquals("http://lan", freshWebdavRoute(memo, "k", 1_000 + WEBDAV_ROUTE_MEMO_MS - 1))
        assertNull(freshWebdavRoute(memo, "k", 1_000 + WEBDAV_ROUTE_MEMO_MS))
        assertNull(freshWebdavRoute(memo, "other", 1_100))
        assertFalse(
            webdavRouteKey("https://a", "http://b", "/p", allowLan = true) ==
                webdavRouteKey("https://a", "http://b", "/p", allowLan = false),
        )
    }

    @Test
    fun oldMetaWithoutRevisionFieldsStillParses() {
        val parsed = parseMeta("""{"kind":"$WEBDAV_SYNC_KIND","version":1,"updatedAt":7}""")
        assertEquals(7, parsed.updatedAt)
        assertTrue(parsed.profileUsage.isEmpty())
        assertEquals(0, parsed.access().syncRev)
        val again = parseMeta(encodeMeta(parsed.copy(updatedAt = 8)))
        assertTrue(sameSyncMeta(parsed, again))
    }

    @Test
    fun propfindDecodesEtagXmlEntitiesExactlyOnceAndKeepsWeakness() {
        val xml = """<D:multistatus xmlns:D="DAV:">
            <D:response><D:href>/dav/a.json</D:href><D:propstat><D:prop><D:getetag>&quot;a&amp;amp;&quot;</D:getetag></D:prop></D:propstat></D:response>
            <D:response><D:href>/dav/b.json</D:href><D:propstat><D:prop><D:getetag>W/&quot;weak&quot;</D:getetag></D:prop></D:propstat></D:response>
            <D:response><D:href>/dav/c.json</D:href><D:propstat><D:prop><D:getetag>&#34;numeric&#x22;</D:getetag></D:prop></D:propstat></D:response>
        </D:multistatus>"""
        val entries = parsePropfindEntries(xml, "https://example.com/dav/")
        assertEquals(listOf("\"a&amp;\"", "W/\"weak\"", "\"numeric\""), entries.map { it.etag })
        assertEquals("\"a&amp;\"", firstGetEtag(xml))
        assertFalse(webdavEtagsMatch("\"a&amp;\"", "\"a&\""))
    }

    @Test
    fun crossClientHashVectorsUseJvmPathOrderAndExactLongValues() {
        val body = javaClass.getResourceAsStream("/webdav/cross-client-v1.json")!!.bufferedReader().use { it.readText() }
        val cases = webdavJson.parseToJsonElement(body).jsonObject.getValue("hashes").jsonArray
        for (element in cases) {
            val vector = element.jsonObject
            val payload = webdavJson.decodeFromJsonElement(ProfilePayload.serializer(), vector.getValue("profile"))
            val name = vector.getValue("name").jsonPrimitive.content
            assertEquals(name, vector.getValue("filesFingerprint").jsonPrimitive.content, fingerprint(payload.files))
            assertEquals(name, vector.getValue("contentHash").jsonPrimitive.content, profileContentHash(payload))
            assertEquals(name, vector.getValue("bodyFingerprint").jsonPrimitive.content, profileBodyFingerprint(payload))
        }
    }

    @Test
    fun crossClientMergeVectorsPreserveAliasCandidatesAndDeletionRanks() {
        val body = javaClass.getResourceAsStream("/webdav/cross-client-v1.json")!!.bufferedReader().use { it.readText() }
        val cases = webdavJson.parseToJsonElement(body).jsonObject.getValue("merges").jsonArray
        for (element in cases) {
            val vector = element.jsonObject
            val name = vector.getValue("name").jsonPrimitive.content
            fun profiles(key: String) = vector.getValue(key).jsonArray.map { webdavJson.decodeFromJsonElement(ProfilePayload.serializer(), it) }
            fun usage(key: String) = vector.getValue(key).jsonArray.map { webdavJson.decodeFromJsonElement(ProfileUsagePayload.serializer(), it) }
            fun tombstones(key: String) = vector.getValue(key).jsonArray.map { webdavJson.decodeFromJsonElement(TombstonePayload.serializer(), it) }
            val projected = projectProfileIdentities(profiles("localProfiles"), profiles("remoteProfiles"), usage("localUsage"), usage("remoteUsage"))
            val result = mergeWebdav(
                projected.localProfiles, projected.remoteProfiles,
                tombstones("localTombstones"), tombstones("remoteTombstones"), access(), access(),
                vector.getValue("now").jsonPrimitive.content.toLong(),
                localUsage = projected.localUsage, remoteUsage = projected.remoteUsage,
            )
            assertEquals(name, profiles("expectedProfiles"), result.profiles)
            assertEquals(name, usage("expectedUsage"), result.profileUsage)
            assertEquals(name, tombstones("expectedTombstones"), result.tombstones)
        }
    }

    @Test
    fun crossClientIdentityChangeVectorsKeepOldAndNewIdentitiesSeparate() {
        val body = javaClass.getResourceAsStream("/webdav/cross-client-v1.json")!!.bufferedReader().use { it.readText() }
        val cases = webdavJson.parseToJsonElement(body).jsonObject.getValue("identityChanges").jsonArray
        for (element in cases) {
            val vector = element.jsonObject
            val name = vector.getValue("name").jsonPrimitive.content
            val result = planProfileIdentityChange(
                webdavJson.decodeFromJsonElement(ProfilePayload.serializer(), vector.getValue("previous")),
                webdavJson.decodeFromJsonElement(ProfilePayload.serializer(), vector.getValue("next")),
                vector.getValue("newUuid").jsonPrimitive.content,
                vector.getValue("writerId").jsonPrimitive.content,
                vector.getValue("now").jsonPrimitive.content.toLong(),
            )
            if (vector.getValue("expectedProfile") == JsonNull) assertNull(name, result)
            else {
                assertEquals(name, webdavJson.decodeFromJsonElement(ProfilePayload.serializer(), vector.getValue("expectedProfile")), result?.profile)
                assertEquals(name, webdavJson.decodeFromJsonElement(TombstonePayload.serializer(), vector.getValue("expectedTombstone")), result?.tombstone)
            }
        }
    }

    @Test
    fun aliasesMergeBodyAndUsageBeforeTheRepresentativeIsPublished() {
        val local = profile("local", source = "https://example.com/sub", updatedAt = 10, syncRev = 1)
        val bodyWinner = profile("body", source = local.source, name = "Body", updatedAt = 20, syncRev = 8)
        val representative = profile("representative", source = local.source, name = "Older body", updatedAt = 500, syncRev = 2)
        val usageOnly = profile("usage", source = local.source, updatedAt = 30)
        val projected = projectProfileIdentities(
            listOf(local),
            listOf(bodyWinner, representative, usageOnly),
            remoteUsage = listOf(ProfileUsagePayload("usage", upload = 42, updatedAt = 1_000)),
        )
        val merged = mergeWebdav(
            projected.localProfiles, projected.remoteProfiles, emptyList(), emptyList(), access(), access(), 2_000,
            localUsage = projected.localUsage, remoteUsage = projected.remoteUsage,
        )
        assertEquals("representative", merged.profiles.single().uuid)
        assertEquals("Body", merged.profiles.single().name)
        assertEquals(42, merged.profiles.single().upload)
        assertEquals(42, merged.profileUsage.single().upload)
        assertEquals(setOf("local", "body", "representative", "usage"), projected.uuidAliases.keys)
        assertEquals(setOf("representative"), projected.uuidAliases.values.toSet())
        assertTrue(merged.tombstones.isEmpty())

        val again = projectProfileIdentities(listOf(local), merged.profiles, remoteUsage = merged.profileUsage)
        val next = mergeWebdav(
            again.localProfiles, again.remoteProfiles, emptyList(), emptyList(), access(), access(), 2_001,
            localUsage = again.localUsage, remoteUsage = again.remoteUsage,
        )
        assertEquals(merged.profiles, next.profiles)
        assertEquals(merged.profileUsage, next.profileUsage)
    }

    @Test
    fun projectedAliasesKeepTheActualRemoteRepresentativesUnknownFields() {
        val body = profile("a", source = "https://example.com/sub", updatedAt = 100, syncRev = 9)
            .copy(preserved = webdavJson.parseToJsonElement("""{"future":"older-alias"}""").jsonObject)
        val representative = body.copy(
            uuid = "z", syncRev = 1,
            preserved = webdavJson.parseToJsonElement("""{"future":"current-representative"}""").jsonObject,
        )
        val projected = projectProfileIdentities(emptyList(), listOf(body, representative))
        val merged = mergeWebdav(
            projected.localProfiles, projected.remoteProfiles, emptyList(), emptyList(), access(), access(), 1_000,
            localUsage = projected.localUsage, remoteUsage = projected.remoteUsage,
        )
        assertEquals("z", merged.profiles.single().uuid)
        assertEquals(9, merged.profiles.single().syncRev)
        assertEquals(representative.preserved, merged.profiles.single().preserved)
    }

    @Test
    fun aliasUsageFallbackIsDecidedBeforeUuidProjection() {
        val legacy = profile("legacy", source = "https://example.com/sub", upload = 10, updatedAt = 500)
        val explicit = legacy.copy(uuid = "explicit", upload = 20, updatedAt = 100, syncRev = 9)
        val projected = projectProfileIdentities(
            emptyList(), listOf(legacy, explicit),
            remoteUsage = listOf(ProfileUsagePayload("explicit", upload = 2, updatedAt = 50)),
        )
        val merged = mergeWebdav(
            projected.localProfiles, projected.remoteProfiles, emptyList(), emptyList(), access(), access(), 1_000,
            localUsage = projected.localUsage, remoteUsage = projected.remoteUsage,
        )
        assertEquals("legacy", merged.profiles.single().uuid)
        assertEquals(9, merged.profiles.single().syncRev)
        assertEquals(10, merged.profiles.single().upload)
        assertEquals(500, merged.profileUsage.single().updatedAt)
    }

    @Test
    fun duplicateAliasesOnOneSideCannotHideTheHigherRevision() {
        val winner = profile("older-clock", source = "https://example.com/sub", name = "kept", updatedAt = 10, syncRev = 5)
        val newer = winner.copy(uuid = "newer-clock", name = "stale", updatedAt = 500, syncRev = 1)
        val merged = mergeWebdav(emptyList(), listOf(winner, newer), emptyList(), emptyList(), access(), access(), 1_000)
        assertEquals("kept", merged.profiles.single().name)
        assertEquals("newer-clock", merged.profiles.single().uuid)
    }

    @Test
    fun changedSubscriptionSourceProducesSeparateRecoverableIdentities() {
        val previous = profile("old", source = "https://example.com/old", updatedAt = 900, syncRev = 4)
        val change = planProfileIdentityChange(previous, previous.copy(source = "https://example.com/new"), "new", "writer", 100)!!
        assertEquals("old", change.tombstone.uuid)
        assertEquals(previous.source, change.tombstone.source)
        assertEquals(900, change.tombstone.deletedAt)
        assertEquals(4, change.tombstone.rev)
        assertEquals("new", change.profile.uuid)
        assertEquals(5, change.profile.syncRev)
        val merged = mergeWebdav(
            listOf(change.profile), listOf(previous), listOf(change.tombstone), emptyList(), access(), access(), 1_000,
        )
        assertEquals(listOf("new"), merged.profiles.map { it.uuid })
        assertNull(planProfileIdentityChange(previous, previous.copy(type = "External"), "unused", "writer", 1_000))
    }

    @Test
    fun unplannedIdentityChangeCannotWriteTwoBodiesToTheSameUuid() {
        val previous = profile("id", source = "https://example.com/old")
        try {
            mergeWebdav(listOf(previous.copy(source = "https://example.com/new")), listOf(previous), emptyList(), emptyList(), access(), access(), 1_000)
            throw AssertionError("identity changes must be planned before merging")
        } catch (err: WebdavException) {
            assertEquals("identity_conflict", err.code)
        }
    }

    private fun profile(
        uuid: String,
        source: String = "https://example.com/$uuid",
        type: String = "Url",
        name: String = uuid,
        updatedAt: Long = 1,
        syncRev: Long = 0,
        upload: Long = 0,
        download: Long = 0,
    ): ProfilePayload {
        return ProfilePayload(
            uuid = uuid,
            name = name,
            type = type,
            source = source,
            upload = upload,
            download = download,
            updatedAt = updatedAt,
            syncRev = syncRev,
            files = listOf(ProfileFilePayload("config.yaml", "e30=")),
        )
    }

    private fun access(
        mode: String = "AcceptAll",
        updatedAt: Long = 0,
        packages: List<String> = emptyList(),
        writerId: String = "",
    ): AccessControlPayload {
        return AccessControlPayload(mode, packages, updatedAt, writerId = writerId)
    }
}
