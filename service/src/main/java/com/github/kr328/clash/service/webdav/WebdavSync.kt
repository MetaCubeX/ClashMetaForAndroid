package com.github.kr328.clash.service.webdav

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger

class WebdavSync(
    private val context: Context,
    private val client: WebdavClient = WebdavClient(),
    private val onProgress: (phase: String, current: Int, total: Int, label: String) -> Unit = { _, _, _, _ -> },
) {
    private val state = WebdavLocalState(context)
    internal var hasDeferred: Boolean = false
        private set

    suspend fun perform(action: String, settings: StoredWebdavSettings): String {
        return gate.withLock {
            withContext(Dispatchers.IO) {
                hasDeferred = false
                try {
                    if (!sameWebdavConnection(settings, WebdavSettingsStore(context).load())) {
                        throw WebdavException("scope_changed", detail = "WebDAV settings changed; retry with the current account and folder")
                    }
                    state.ensureScopeCurrent()
                    val message = execute(action, settings)
                    val now = System.currentTimeMillis()
                    state.ensureScopeCurrent()
                    state.update {
                        it.copy(
                            lastSyncAt = now,
                            lastPullAt = if (action == ACTION_PUSH || action == ACTION_TEST) it.lastPullAt else now,
                            lastMessage = message,
                        )
                    }
                    message
                } catch (err: WebdavException) {
                    val message = err.localize(context)
                    runCatching { state.update { it.copy(lastMessage = message) } }
                    throw IllegalStateException(message)
                } catch (err: IllegalStateException) {
                    throw err
                } catch (err: Exception) {
                    val message = err.message?.ifBlank { null } ?: context.getString(R.string.webdav_error_generic, err.javaClass.simpleName)
                    runCatching { state.update { it.copy(lastMessage = message) } }
                    throw IllegalStateException(message)
                }
            }
        }
    }

    private suspend fun execute(action: String, settings: StoredWebdavSettings): String {
        httpConcurrency.set(WEBDAV_HTTP_CONCURRENCY)
        progressFloor.set(0)
        progress("connect", 0, 0, "")
        val config = config(settings)
        if (action == ACTION_TEST) {
            progress("list", 0, 0, "")
            runLimited { client.list(config) }
            return context.getString(R.string.webdav_connected)
        }
        return runOnce(config, action)
    }

    private suspend fun runOnce(config: WebdavConfig, action: String): String {
        val profiles = WebdavProfiles(context)
        val exported = profiles.exportLocal(includeUnmanaged = action == ACTION_PUSH)
        val remote = download(config)
        val identities = projectProfileIdentities(
            exported.profiles,
            remote.profiles,
            exported.profileUsage,
            remote.meta.profileUsage,
        )
        val local = exported.copy(profiles = identities.localProfiles, profileUsage = identities.localUsage)
        val now = System.currentTimeMillis()
        var merged = mergeWebdav(
            localProfiles = local.profiles,
            remoteProfiles = identities.remoteProfiles,
            localTombstones = local.tombstones,
            remoteTombstones = remote.meta.tombstones,
            localAccess = local.access,
            remoteAccess = remote.meta.access(),
            now = now,
            localStrategies = local.strategies,
            remoteStrategies = remote.meta.appsStrategies,
            localStrategyTombstones = local.strategyTombstones,
            remoteStrategyTombstones = remote.meta.appsStrategyTombstones,
            localActiveStrategy = local.activeAppsStrategy,
            localActiveStrategyUpdatedAt = local.activeAppsStrategyUpdatedAt,
            remoteActiveStrategy = remote.meta.activeAppsStrategy,
            remoteActiveStrategyUpdatedAt = remote.meta.activeAppsStrategyUpdatedAt,
            localActiveStrategySyncRev = local.activeAppsStrategySyncRev,
            localActiveStrategyWriterId = local.activeAppsStrategyWriterId,
            remoteActiveStrategySyncRev = remote.meta.activeAppsStrategySyncRev,
            remoteActiveStrategyWriterId = remote.meta.activeAppsStrategyWriterId,
            localUsage = local.profileUsage,
            remoteUsage = identities.remoteUsage,
        )
        val counts = if (action == ACTION_PUSH) {
            ApplyCounts(downloaded = 0, removed = 0, skipped = 0)
        } else {
            profiles.apply(merged, local, now) { current, total, label ->
                progress("apply", current, total, label)
            }
        }
        var deferred = counts.skipped
        val deferredIds = counts.deferredProfiles + counts.deferredProfiles.map { identities.uuidAliases[it] ?: it }
        val uploaded = if (action == ACTION_PULL) {
            remember(remote.etags, remote.fingerprints, keepNames(merged, remote.unreadable), remote.unreadable, remote.profiles, deferredIds)
            0
        } else {
            val outcome = upload(config, remote, merged, local, now, action, identities.uuidAliases, deferredIds)
            merged = outcome.merged
            deferred = maxOf(deferred, outcome.skipped)
            remember(outcome.etags, outcome.fingerprints, keepNames(outcome.merged, remote.unreadable), remote.unreadable, outcome.remoteProfiles, deferredIds + outcome.deferredProfiles)
            outcome.uploaded
        }
        if (action != ACTION_PUSH) {
            state.update { current ->
                current.copy(
                    tombstones = retainMergedTombstones(merged.tombstones, local.tombstones, current.tombstones, now),
                    strategyTombstones = retainMergedTombstones(
                        merged.strategyTombstones,
                        local.strategyTombstones,
                        current.strategyTombstones,
                        now,
                    ),
                )
            }
        }
        if (deferred > 0) {
            hasDeferred = true
            return context.getString(R.string.webdav_sync_deferred, deferred)
        }
        if (counts.downloaded == 0 && uploaded == 0 && counts.removed == 0) {
            return context.getString(R.string.webdav_sync_idle)
        }
        return context.getString(R.string.webdav_sync_done, counts.downloaded, uploaded, counts.removed)
    }

    private suspend fun download(config: WebdavConfig): RemoteBundle {
        progress("list", 0, 0, "")
        val snapshot = runLimited { client.readSnapshot(config) }
        val root = snapshot.root
        val nested = snapshot.profiles
        val cache = state.read()
        val etags = linkedMapOf<String, String?>()
        val fingerprints = linkedMapOf<String, String>()
        val profiles = mutableListOf<ProfilePayload>()
        val listedMetaEtag = root.firstOrNull { !it.collection && it.name == WEBDAV_META_FILE }?.etag
        val metaGot = snapshot.meta
        val readMeta = interpretRemoteMeta(metaGot.found, metaGot.body, metaGot.etag, listedMetaEtag)
        if (readMeta.recordEtag) etags[WEBDAV_META_FILE] = readMeta.etag
        val files = nested.filter { !it.collection && !isWebdavTempFileName(it.name) && it.name.endsWith(".json") }
        val toFetch = mutableListOf<WebdavListEntry>()
        for (entry in files) {
            val name = "$WEBDAV_PROFILES_DIR/${entry.name}"
            val cachedProfile = cache.remoteProfiles[name]
            val cachedFp = cachedProfile?.let(::profileBodyFingerprint)
            if (shouldSkipProfileGet(entry.etag, cache.remoteEtags[name], cachedFp, cachedProfile != null)) {
                etags[name] = entry.etag
                if (!cachedFp.isNullOrBlank()) fingerprints[name] = cachedFp
                profiles.add(cachedProfile!!)
            } else {
                toFetch.add(entry)
            }
        }
        progressFloor.set(0)
        val fetched = fetchProfiles(config, toFetch)
        val unreadable = mutableSetOf<String>()
        for (item in fetched) {
            if (item.absent) continue
            etags[item.name] = item.etag
            when (val read = item.read) {
                ProfileBodyRead.Unreadable -> unreadable.add(item.name)
                is ProfileBodyRead.Ok -> {
                    profiles.add(read.profile)
                    fingerprints[item.name] = read.fingerprint
                }
                null -> unreadable.add(item.name)
            }
        }
        return RemoteBundle(profiles, readMeta.meta, etags, unreadable, fingerprints)
    }

    private suspend fun fetchProfiles(config: WebdavConfig, files: List<WebdavListEntry>): List<FetchedProfile> {
        if (files.isEmpty()) return emptyList()
        val limit = Semaphore(httpConcurrency.get().coerceAtLeast(1))
        val done = AtomicInteger(0)
        return coroutineScope {
            files.map { entry ->
                async(Dispatchers.IO) {
                    limit.withPermit {
                        val item = runLimited { fetchOne(config, entry) }
                        val current = done.incrementAndGet()
                        progressMonotonic("download", current, files.size, entry.name.removeSuffix(".json"))
                        item
                    }
                }
            }.awaitAll()
        }
    }

    private fun fetchOne(config: WebdavConfig, entry: WebdavListEntry): FetchedProfile {
        val name = "$WEBDAV_PROFILES_DIR/${entry.name}"
        val got = client.get(config, name)
        if (!got.found) return FetchedProfile(name, null, null, absent = true)
        val parsed = readProfileBody(got.body)
        val read = if (parsed is ProfileBodyRead.Ok && profileFileName(parsed.profile.uuid) != name) {
            ProfileBodyRead.Unreadable
        } else parsed
        return FetchedProfile(name, got.etag ?: entry.etag, read, absent = false)
    }

    private suspend fun upload(
        config: WebdavConfig,
        remote: RemoteBundle,
        merged: MergeResult,
        local: LocalSnapshot,
        now: Long,
        action: String,
        aliases: Map<String, String>,
        deferred: Set<String>,
    ): UploadOutcome {
        runLimited { client.ensureParents(config) }
        runLimited { client.verifyConditionalRequests(config) }
        val etags = LinkedHashMap(remote.etags)
        val fingerprints = LinkedHashMap(remote.fingerprints)
        val remoteProfiles = remote.profiles.associateBy { profileFileName(it.uuid) }.toMutableMap()
        fun isDeferred(uuid: String) = uuid in deferred || aliases[uuid]?.let { it in deferred } == true
        val writable = merged.profiles.filter { profileFileName(it.uuid) !in remote.unreadable && !isDeferred(it.uuid) }
        val pending = writable.filter { profile ->
            val name = profileFileName(profile.uuid)
            val known = fingerprints[name]
            known == null || known != profileBodyFingerprint(profile)
        }
        val replacements = linkedMapOf<String, ProfilePayload>()
        progressFloor.set(0)
        var uploaded = 0
        if (pending.isNotEmpty()) {
            val limit = Semaphore(httpConcurrency.get().coerceAtLeast(1))
            val done = AtomicInteger(0)
            val written = coroutineScope {
                pending.map { profile ->
                    async(Dispatchers.IO) {
                        limit.withPermit {
                            val changed = putProfile(config, profile, etags, fingerprints, onRemote = { actual ->
                                synchronized(remoteProfiles) { remoteProfiles[profileFileName(actual.uuid)] = actual }
                            }) { winner ->
                                if (
                                    profileBodyFingerprint(winner) != profileBodyFingerprint(profile) ||
                                    !sameProfileUsage(winner, profile)
                                ) {
                                    synchronized(replacements) { replacements[winner.uuid] = winner }
                                }
                            }
                            val current = done.incrementAndGet()
                            progressMonotonic("upload", current, pending.size, profile.name)
                            changed
                        }
                    }
                }.awaitAll()
            }
            uploaded = written.count { it }
        }
        val kept = merged.profiles.map { profileFileName(it.uuid) }.toSet()
        var next = merged
        if (replacements.isNotEmpty()) {
            next = next.copy(profiles = next.profiles.map { replacements[it.uuid] ?: it })
        }
        val publishable = next.copy(
            profileUsage = next.profileUsage.filterNot { isDeferred(it.uuid) } + remote.meta.profileUsage.filter { isDeferred(it.uuid) },
            tombstones = next.tombstones.filterNot { isDeferred(it.uuid) } + remote.meta.tombstones.filter { isDeferred(it.uuid) },
        )
        val intended = metaFrom(publishable, System.currentTimeMillis(), legacyAccess = merged.access)
            .copy(preserved = remote.meta.preserved)
        val metaChanged = uploaded > 0 || !sameSyncMeta(intended, remote.meta)
        var finalMeta = intended
        if (metaChanged) {
            val written = putMeta(config, etags, intended)
            finalMeta = written
            next = next.copy(
                tombstones = written.tombstones,
                access = written.access(),
                strategies = written.appsStrategies,
                strategyTombstones = written.appsStrategyTombstones,
                activeAppsStrategy = written.activeAppsStrategy,
                activeAppsStrategyUpdatedAt = written.activeAppsStrategyUpdatedAt,
                activeAppsStrategySyncRev = written.activeAppsStrategySyncRev,
                activeAppsStrategyWriterId = written.activeAppsStrategyWriterId,
                profileUsage = written.profileUsage,
            )
        }
        // Retain duplicate aliases during convergence. Only an explicit, winning
        // user tombstone permits deleting its readable body, including aliases.
        val doomed = remoteProfiles.entries.filter { (name, profile) ->
            isDeletableRemoteProfile(name, kept, remote.unreadable) && !isDeferred(profile.uuid) &&
                finalMeta.tombstones.any { tombstone ->
                    matchesTombstone(tombstone, profile) && tombstoneBeats(tombstone, profile.updatedAt, profile.syncRev)
                }
        }.map { it.key }
        deleteRemote(config, doomed, etags)
        for (name in doomed) remoteProfiles.remove(name)
        var skipped = 0
        var deferredProfiles = emptySet<String>()
        if (
            action != ACTION_PUSH &&
            (replacements.isNotEmpty() || !sameSyncMeta(finalMeta, metaFrom(merged, legacyAccess = merged.access)))
        ) {
            val profiles = next.profiles.map { profile ->
                val usage = next.profileUsage.find { it.uuid == profile.uuid }
                if (usage == null) profile else profile.copy(
                    upload = usage.upload,
                    download = usage.download,
                    total = usage.total,
                    expire = usage.expire,
                )
            }
            val applied = WebdavProfiles(context).apply(next.copy(profiles = profiles), local, now)
            skipped = applied.skipped
            deferredProfiles = applied.deferredProfiles
        }
        return UploadOutcome(uploaded, etags, fingerprints, next, skipped, remoteProfiles.values.toList(), deferredProfiles)
    }

    private fun putProfile(
        config: WebdavConfig,
        initial: ProfilePayload,
        etags: MutableMap<String, String?>,
        fingerprints: MutableMap<String, String>,
        onRemote: (ProfilePayload) -> Unit,
        onWinner: (ProfilePayload) -> Unit,
    ): Boolean {
        val name = profileFileName(initial.uuid)
        var current = initial
        for (attempt in 0 until 3) {
            val snapshot = synchronized(etags) {
                val known = fingerprints[name]
                if (known != null && known == profileBodyFingerprint(current) && name in etags) {
                    null
                } else {
                    etags[name] to (name !in etags)
                }
            }
            if (snapshot == null) {
                onWinner(current)
                return false
            }
            val (ifMatch, createOnly) = snapshot
            try {
                val result = runLimited {
                    client.put(config, name, encodeProfile(current), ifMatch = ifMatch, createOnly = createOnly)
                }
                synchronized(etags) {
                    etags[name] = result.etag
                    fingerprints[name] = profileBodyFingerprint(current)
                }
                onRemote(current)
                onWinner(current)
                return true
            } catch (err: WebdavConflictException) {
                if (attempt == 2) throw err
                Thread.sleep(50L + ThreadLocalRandom.current().nextLong(151))
                val got = runLimited { client.get(config, name) }
                if (!got.found) {
                    synchronized(etags) {
                        etags.remove(name)
                        fingerprints.remove(name)
                    }
                    continue
                }
                when (val read = readProfileBody(got.body)) {
                    ProfileBodyRead.Unreadable -> throw err
                    is ProfileBodyRead.Ok -> {
                        if (read.profile.uuid != initial.uuid || profileIdentity(read.profile) != profileIdentity(initial)) {
                            throw WebdavException("conflict", 412, "The remote profile identity changed; retry a complete sync")
                        }
                        onRemote(read.profile)
                        synchronized(etags) {
                            etags[name] = got.etag
                            fingerprints[name] = read.fingerprint
                        }
                        val winner = overlayUsage(
                            mergeProfile(current, read.profile),
                            listOf(usageFromProfile(current), usageFromProfile(read.profile)),
                        ).copy(preserved = read.profile.preserved)
                        if (profileBodyFingerprint(winner) == read.fingerprint && sameProfileUsage(winner, read.profile)) {
                            onWinner(winner)
                            return false
                        }
                        current = winner
                    }
                }
            }
        }
        return false
    }

    private suspend fun deleteRemote(
        config: WebdavConfig,
        names: List<String>,
        etags: MutableMap<String, String?>,
    ): Int {
        if (names.isEmpty()) return 0
        progressFloor.set(0)
        val limit = Semaphore(httpConcurrency.get().coerceAtLeast(1))
        val done = AtomicInteger(0)
        val removed = AtomicInteger(0)
        coroutineScope {
            names.map { name ->
                async(Dispatchers.IO) {
                    limit.withPermit {
                        val ifMatch = synchronized(etags) { etags[name] }
                        runLimited { client.delete(config, name, ifMatch = ifMatch) }
                        synchronized(etags) { etags.remove(name) }
                        removed.incrementAndGet()
                        val current = done.incrementAndGet()
                        progressMonotonic(
                            "delete",
                            current,
                            names.size,
                            name.substringAfterLast('/').removeSuffix(".json"),
                        )
                    }
                }
            }.awaitAll()
        }
        return removed.get()
    }

    private fun putMeta(
        config: WebdavConfig,
        etags: MutableMap<String, String?>,
        initial: SyncMetaPayload,
    ): SyncMetaPayload {
        var meta = initial
        for (attempt in 0 until 3) {
            val ifMatch = etags[WEBDAV_META_FILE]
            val createOnly = WEBDAV_META_FILE !in etags
            try {
                val result = runLimited {
                    client.put(
                        config,
                        WEBDAV_META_FILE,
                        encodeMeta(meta),
                        ifMatch = ifMatch,
                        createOnly = createOnly,
                    )
                }
                etags[WEBDAV_META_FILE] = result.etag
                return meta
            } catch (err: WebdavConflictException) {
                if (attempt == 2) throw err
                Thread.sleep(50L + ThreadLocalRandom.current().nextLong(151))
                val got = runLimited { client.get(config, WEBDAV_META_FILE) }
                if (!got.found) {
                    etags.remove(WEBDAV_META_FILE)
                    continue
                }
                if (got.body.isBlank()) throw WebdavException("broken_meta")
                val fresh = parseMeta(got.body)
                etags[WEBDAV_META_FILE] = got.etag
                val merged = mergeMeta(meta, fresh, System.currentTimeMillis()).copy(preserved = fresh.preserved)
                if (sameSyncMeta(merged, fresh)) return fresh
                meta = merged
            }
        }
        return meta
    }

    private fun remember(
        etags: Map<String, String?>,
        fingerprints: Map<String, String>,
        keep: Set<String>,
        unreadable: Set<String>,
        profiles: List<ProfilePayload>,
        deferred: Set<String>,
    ) {
        state.update { current ->
            val nextEtags = linkedMapOf<String, String>()
            val nextFp = linkedMapOf<String, String>()
            for (name in keep) {
                val etag = etags[name]
                if (!etag.isNullOrBlank()) nextEtags[name] = etag
                if (name in unreadable || profileUuidFromFileName(name)?.let { it in deferred } == true) continue
                val fp = fingerprints[name]
                if (!fp.isNullOrBlank()) nextFp[name] = fp
            }
            current.copy(
                remoteEtags = nextEtags,
                pushedFingerprint = nextFp,
                remoteProfiles = profiles.associateBy { profileFileName(it.uuid) }
                    .filterKeys { it in keep && it !in unreadable },
            )
        }
    }

    private fun keepNames(merged: MergeResult, unreadable: Set<String>): Set<String> {
        return merged.profiles.map { profileFileName(it.uuid) }.toSet() + unreadable + WEBDAV_META_FILE
    }

    private fun config(settings: StoredWebdavSettings): WebdavConfig {
        if (settings.username.isBlank()) throw WebdavException("empty_user")
        if (settings.password.isEmpty()) throw WebdavException("empty_password")
        val lan = normalizeWebdavLanUrl(settings.lanUrl)
        val allowLan = prefersWebdavLan(context)
        val key = webdavRouteKey(settings.url, lan, settings.remotePath, allowLan)
        val now = System.currentTimeMillis()
        val cached = freshWebdavRoute(routeMemo, key, now)
        val base = if (cached != null) {
            cached
        } else {
            val resolved = resolveWebdavBaseUrl(
                url = settings.url,
                lanUrl = lan,
                remotePath = settings.remotePath,
                allowLan = allowLan,
                probe = { url -> client.reachable(url) },
            )
            routeMemo = WebdavRouteMemo(key, resolved, now)
            resolved
        }
        val allowPrivateHttp = base.trimEnd('/') == lan.trimEnd('/')
        joinWebdavUrl(base, resolveWebdavRemoteFolder(settings.remotePath), allowPrivateHttp)
        return WebdavConfig(
            url = base,
            username = settings.username.trim(),
            password = settings.password,
            remotePath = resolveWebdavRemoteFolder(settings.remotePath),
            clientId = state.clientId(),
            allowPrivateHttp = allowPrivateHttp,
        )
    }

    private fun <T> runLimited(block: () -> T): T {
        try {
            state.ensureScopeCurrent()
            return block().also { state.ensureScopeCurrent() }
        } catch (err: WebdavException) {
            if (err.status != 429 && err.status != 423) throw err
            noteThrottled()
            throw err
        }
    }

    private fun noteThrottled() {
        if (httpConcurrency.getAndSet(WEBDAV_HTTP_CONCURRENCY_THROTTLED) != WEBDAV_HTTP_CONCURRENCY_THROTTLED) {
            Log.i("WebDAV concurrency dropped to $WEBDAV_HTTP_CONCURRENCY_THROTTLED")
        }
    }

    private data class FetchedProfile(
        val name: String,
        val etag: String?,
        val read: ProfileBodyRead?,
        val absent: Boolean,
    )

    private data class RemoteBundle(
        val profiles: List<ProfilePayload>,
        val meta: SyncMetaPayload,
        val etags: Map<String, String?>,
        val unreadable: Set<String>,
        val fingerprints: Map<String, String>,
    )

    private data class UploadOutcome(
        val uploaded: Int,
        val etags: Map<String, String?>,
        val fingerprints: Map<String, String>,
        val merged: MergeResult,
        val skipped: Int,
        val remoteProfiles: List<ProfilePayload>,
        val deferredProfiles: Set<String>,
    )

    companion object {
        const val ACTION_TEST = "test"
        const val ACTION_SYNC = "sync"
        const val ACTION_PULL = "pull"
        const val ACTION_PUSH = "push"
        const val ACTION_SCHEDULE = "schedule"

        private val gate = Mutex()
        private val httpConcurrency = AtomicInteger(WEBDAV_HTTP_CONCURRENCY)
        private val progressFloor = AtomicInteger(0)
        private var routeMemo: WebdavRouteMemo? = null
    }

    private val progressLock = Any()

    private fun progressMonotonic(phase: String, current: Int, total: Int, label: String) {
        synchronized(progressLock) {
            if (current < progressFloor.get()) return
            progressFloor.set(current)
            progress(phase, current, total, label)
        }
    }

    private fun progress(phase: String, current: Int, total: Int, label: String) {
        try {
            onProgress(phase, current, total, label)
        } catch (_: Exception) {
        }
    }
}

internal fun prefersWebdavLan(context: Context): Boolean {
    val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
    @Suppress("DEPRECATION")
    val networks = connectivity.allNetworks
    return networks.any { network ->
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return@any false
        // A Wi-Fi network that failed internet validation can still reach a LAN server.
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}

internal data class RemoteMetaRead(
    val meta: SyncMetaPayload,
    val etag: String?,
    val recordEtag: Boolean,
)

internal fun interpretRemoteMeta(
    found: Boolean,
    body: String,
    getEtag: String?,
    listedEtag: String?,
): RemoteMetaRead {
    if (!found) return RemoteMetaRead(SyncMetaPayload(), null, recordEtag = false)
    if (body.isBlank()) throw WebdavException("broken_meta")
    return RemoteMetaRead(parseMeta(body), getEtag ?: listedEtag, recordEtag = true)
}

private fun WebdavException.localize(context: Context): String {
    return when (code) {
        "empty_url" -> context.getString(R.string.webdav_error_empty_url)
        "bad_url" -> context.getString(R.string.webdav_error_bad_url)
        "https" -> context.getString(R.string.webdav_error_https)
        "lan_http" -> context.getString(R.string.webdav_error_lan_http)
        "lan_offline" -> context.getString(R.string.webdav_error_lan_offline)
        "lan_unreachable" -> context.getString(R.string.webdav_error_lan_unreachable)
        "empty_user" -> context.getString(R.string.webdav_error_empty_user)
        "empty_password" -> context.getString(R.string.webdav_error_empty_password)
        "auth" -> context.getString(R.string.webdav_error_auth)
        "redirect" -> context.getString(R.string.webdav_error_redirect)
        "timeout" -> context.getString(R.string.webdav_error_timeout)
        "connect" -> context.getString(R.string.webdav_error_connect)
        "locked" -> context.getString(R.string.webdav_error_locked)
        "not_found", "conflict_parent" -> context.getString(R.string.webdav_error_not_found)
        "conflict" -> context.getString(R.string.webdav_error_conflict)
        "bad_version" -> context.getString(R.string.webdav_error_bad_version)
        "broken_meta" -> context.getString(R.string.webdav_error_broken)
        "too_large" -> context.getString(R.string.webdav_error_too_large, detail.ifBlank { "?" })
        "http" -> detail.ifBlank { context.getString(R.string.webdav_error_generic, status ?: 0) }
        else -> detail.ifBlank { context.getString(R.string.webdav_error_generic, code) }
    }
}
