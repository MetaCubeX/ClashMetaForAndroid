package com.github.kr328.clash.service

import android.content.Context
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.AgeKeyUrl
import com.github.kr328.clash.common.util.SubscriptionNameGuesser
import com.github.kr328.clash.common.util.SubscriptionUsage
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProfileSnapshot
import com.github.kr328.clash.service.data.Database
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Pending
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.data.Selection
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.branding.BrandRefresh
import com.github.kr328.clash.service.branding.BrandStore
import com.github.kr328.clash.service.model.ProxyGroupPreviewRow
import com.github.kr328.clash.service.model.ProxyTransportInfo
import com.github.kr328.clash.service.model.RuleState
import com.github.kr328.clash.service.model.YamlPreview
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.service.remote.IProfileManager
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.DnsHostsConfig
import com.github.kr328.clash.service.util.DnsHostsYamlEdit
import com.github.kr328.clash.service.util.TunnelsConfig
import com.github.kr328.clash.service.util.TunnelsYamlEdit
import com.github.kr328.clash.service.util.FetchHeadersFile
import com.github.kr328.clash.service.util.directoryLastModified
import com.github.kr328.clash.service.util.generateProfileUUID
import com.github.kr328.clash.service.util.ProfileComposer
import com.github.kr328.clash.service.util.ProfileMigration
import com.github.kr328.clash.service.util.ProfileOverlay
import com.github.kr328.clash.service.util.PreviewResourceLimits
import com.github.kr328.clash.service.util.UserLayerStore
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.sendProfileUpdateCompleted
import com.github.kr328.clash.service.util.sendProfileUpdateFailed
import com.github.kr328.clash.service.util.ProxyGroupsYamlPreview
import com.github.kr328.clash.service.util.ProxyTransportYamlPreview
import com.github.kr328.clash.service.util.ProxyYamlPreview
import com.github.kr328.clash.service.util.RuleApplyService
import com.github.kr328.clash.service.util.ProxyDialerYamlEdit
import com.github.kr328.clash.service.util.ProxyGroupsYamlEdit
import com.github.kr328.clash.service.util.ProxyProvidersYamlEdit
import com.github.kr328.clash.service.util.RuleProvidersYamlEdit
import com.github.kr328.clash.service.util.YamlPreviewSupport
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.sendProfileChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.util.*

class ProfileManager(private val context: Context) : IProfileManager,
    CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val store = ServiceStore(context)
    private val ruleApplyService = RuleApplyService(context)
    private val previewJson = Json { ignoreUnknownKeys = true }
    private val previewCache = LinkedHashMap<String, CachedPreview>()

    /** User edit layer (config-overlay-architecture): edits live here as intent, not in config.yaml. */
    private val userLayerStore = UserLayerStore(context.importedDir)

    /** Geo-data URLs resolved from current settings (rule rendering / composition need them). */
    /**
     * Ensure the `subscription.yaml` base exists and run the one-time legacy migration (baked edits
     * in `config.yaml` → `user_layer.json`) on first contact. E-01/E-05: the editors now write their
     * intent to the user layer directly, so we no longer re-extract the whole layer from config.yaml
     * on every edit — the layer, not config.yaml, is the source of truth. (Name kept for its call
     * sites; effectively "ensure base + migrate once".)
     */
    private fun syncLayerFromConfig(uuid: UUID) {
        val dir = File(context.importedDir, uuid.toString())
        if (!File(dir, "config.yaml").isFile) return
        val parse: (File) -> com.github.kr328.clash.core.model.ProfileSnapshot? =
            { d -> runCatching { Clash.parseProfileSnapshot(d) }.getOrNull() }
        ProfileMigration.migrateIfNeeded(
            profileDir = dir,
            uuid = uuid,
            store = userLayerStore,
            rulesStateJson = runCatching { ruleApplyService.readStateJson(uuid) }.getOrNull(),
            dnsHostsManaged = store.isDnsHostsManaged(uuid),
            tunnelsManaged = store.isTunnelsManaged(uuid),
            parseSnapshot = parse,
        )
    }

    /**
     * In-memory cache of parsed [ProfileSnapshot] keyed by profile UUID.
     * The dashboard ticker re-queries readProxyGroupsPreview /
     * readProxyTransports / readProxyEntryYaml on every tick — without
     * caching, each tick re-runs mihomo's full ParseRawConfig pipeline
     * over a multi-MB config.yaml, which is the dominant cost on the
     * service IPC path for users with heavy subscriptions.
     *
     * Invalidation is automatic via the config.yaml lastModified
     * timestamp — subscription updates / re-imports rewrite the file,
     * the next reader sees a fresh mtime and re-parses. The entry
     * itself only needs explicit removal on profile delete (memory
     * hygiene; nothing else reads it after deletion).
     *
     * Access guarded by [snapshotCacheLock]. Parse runs *outside* the
     * lock so concurrent readers of different profiles never block on
     * each other — a race between two readers of the same profile
     * just causes a redundant parse (parse is idempotent, last writer
     * to the map wins).
     */
    private data class CachedSnapshot(val lastModified: Long, val snapshot: ProfileSnapshot)
    private val snapshotCache = LinkedHashMap<UUID, CachedSnapshot>()
    private val snapshotCacheLock = Any()
    // Serializes nextProfileOrder() + Pending insert pairs. Without it, two concurrent
    // imports (e.g. auto-update + manual click) can both read the same MAX(profileOrder)
    // and produce duplicate ordering values that compare non-deterministically.
    private val profileOrderLock = Mutex()

    /**
     * UUIDs that currently have a manual [update] in flight. The second click
     * on the "refresh" button arrives while the first is still running and
     * would otherwise start a parallel fetch — that's what produced the
     * "io read/write on closed pipe" errors in the wild. Set membership is
     * the only signal; entries are removed in a `finally` so a crash or
     * coroutine cancellation can't strand a profile in "updating forever".
     */
    private val updatingUuids = java.util.Collections.synchronizedSet(mutableSetOf<UUID>())

    private data class CachedFile(
        val relativePath: String,
        val sourceHash: String,
        val proposedYaml: String,
        val providerFile: Boolean = false,
    )

    private data class CachedPreview(
        val uuid: UUID,
        val files: List<CachedFile>,
        val ruleStateJson: String? = null,
        /**
         * Overlay: a layer mutation to apply when this preview is committed — for edits whose user
         * delta cannot be re-extracted from config.yaml (raw proxy-/rule-provider YAML). In-memory
         * only (CachedPreview is never serialized), so a closure is fine. config-overlay-architecture.
         */
        val layerMutation: ((com.github.kr328.clash.service.util.UserLayer) -> com.github.kr328.clash.service.util.UserLayer)? = null,
    )

    init {
        launch {
            Database.database //.init

            ProfileReceiver.rescheduleAll(context)
        }
    }

    /**
     * Return a parsed ProfileSnapshot for [configFile], reusing the cached
     * one when the file's lastModified hasn't advanced. Callers can hand
     * this to YAML preview helpers (ProxyGroupsYamlPreview /
     * ProxyTransportYamlPreview / ProxyYamlPreview) without re-running
     * mihomo's parser on every dashboard tick.
     */
    private fun getOrParseSnapshot(uuid: UUID, configFile: File): ProfileSnapshot {
        val lastMod = configFile.lastModified()
        synchronized(snapshotCacheLock) {
            val cached = snapshotCache[uuid]
            if (cached != null && cached.lastModified == lastMod) {
                // LRU touch — promote to most-recently-used.
                snapshotCache.remove(uuid)
                snapshotCache[uuid] = cached
                return cached.snapshot
            }
        }
        val parsed = Clash.parseProfileSnapshot(configFile.parentFile!!)
        synchronized(snapshotCacheLock) {
            snapshotCache[uuid] = CachedSnapshot(lastMod, parsed)
            while (snapshotCache.size > SNAPSHOT_CACHE_MAX) {
                snapshotCache.remove(snapshotCache.keys.first())
            }
        }
        return parsed
    }

    private fun invalidateSnapshotCache(uuid: UUID) {
        synchronized(snapshotCacheLock) {
            snapshotCache.remove(uuid)
        }
    }

    companion object {
        // Power users may juggle a handful of subscriptions; keep enough
        // headroom that switching between them stays hot, but bound the
        // total memory cost — parsed snapshots can reach a few hundred KB
        // on heavy configs.
        private const val SNAPSHOT_CACHE_MAX = 4
    }

    override suspend fun create(type: Profile.Type, name: String, source: String): UUID {
        val uuid = generateProfileUUID()
        // Operator links may carry the age decryption key in the URL fragment
        // (`…#AGE-SECRET-KEY-…`) — split it into the profile field so the user
        // never types a key by hand. Every import surface (URL form, clipboard,
        // QR, deep-link) funnels through here.
        val split = AgeKeyUrl.split(source)
        profileOrderLock.withLock {
            val profileOrder = nextProfileOrder()
            val pending = Pending(
                uuid = uuid,
                name = name,
                type = type,
                source = split.source,
                interval = 0,
                upload = 0,
                total = 0,
                download = 0,
                expire = 0,
                profileOrder = profileOrder,
                ageSecretKey = split.ageSecretKey,
            )

            PendingDao().insert(pending)
        }

        context.pendingDir.resolve(uuid.toString()).apply {
            deleteRecursively()
            mkdirs()

            @Suppress("BlockingMethodInNonBlockingContext")
            resolve("config.yaml").createNewFile()
            resolve("providers").mkdir()
        }

        return uuid
    }

    override suspend fun clone(uuid: UUID): UUID {
        val newUUID = generateProfileUUID()

        val imported = ImportedDao().queryByUUID(uuid)
            ?: throw FileNotFoundException("profile $uuid not found")

        cloneImportedFiles(uuid, newUUID)

        profileOrderLock.withLock {
            val profileOrder = nextProfileOrder()
            val pending = Pending(
                uuid = newUUID,
                name = imported.name,
                type = Profile.Type.File,
                source = imported.source,
                interval = imported.interval,
                upload = imported.upload,
                total = imported.total,
                download = imported.download,
                expire = imported.expire,
                profileOrder = profileOrder,
                ageSecretKey = imported.ageSecretKey,
            )

            PendingDao().insert(pending)
        }

        return newUUID
    }

    override suspend fun patch(uuid: UUID, name: String, source: String, interval: Long, ageSecretKey: String?) {
        // A key pasted as part of the URL (fragment) wins over the separate
        // field — it is the more deliberate input, and leaving it inside
        // `source` would leak it into every place that renders the URL.
        val fragmentSplit = AgeKeyUrl.split(source)
        @Suppress("NAME_SHADOWING") val source = fragmentSplit.source
        @Suppress("NAME_SHADOWING") val ageSecretKey = fragmentSplit.ageSecretKey ?: ageSecretKey
        val locked = store.subscriptionShareLinksLockedFor(uuid)
        val resolvedSource =
            if (!locked) {
                source
            } else {
                PendingDao().queryByUUID(uuid)?.source
                    ?: ImportedDao().queryByUUID(uuid)?.source
                    ?: source
            }
        val pending = PendingDao().queryByUUID(uuid)

        if (pending == null) {
            val imported = ImportedDao().queryByUUID(uuid)
                ?: throw FileNotFoundException("profile $uuid not found")

            cloneImportedFiles(uuid)

            PendingDao().insert(
                Pending(
                    uuid = imported.uuid,
                    name = name,
                    type = imported.type,
                    source = resolvedSource,
                    interval = interval,
                    upload = 0,
                    total = 0,
                    download = 0,
                    expire = 0,
                    profileOrder = imported.profileOrder,
                    ageSecretKey = ageSecretKey?.takeIf { it.isNotBlank() },
                )
            )
        } else {
            val newPending = pending.copy(
                name = name,
                source = resolvedSource,
                interval = interval,
                upload = 0,
                total = 0,
                download = 0,
                expire = 0,
                ageSecretKey = ageSecretKey?.takeIf { it.isNotBlank() },
            )

            PendingDao().update(newPending)
        }
    }

    override suspend fun renameImported(uuid: UUID, name: String) {
        withContext(Dispatchers.IO) {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@withContext
            val imported = ImportedDao().queryByUUID(uuid) ?: return@withContext
            if (imported.name == trimmed) return@withContext
            ImportedDao().update(imported.copy(name = trimmed))
            // Mirror to a pending draft if one happens to exist (full edit
            // flow in progress) — otherwise we'd silently overwrite the
            // user's in-progress edit on the next commit. No-op when no draft.
            PendingDao().queryByUUID(uuid)?.let { pending ->
                PendingDao().update(pending.copy(name = trimmed))
            }
            context.sendProfileChanged(uuid)
        }
    }

    override suspend fun applySubscriptionUpdateInterval(uuid: UUID, intervalMillis: Long) {
        withContext(Dispatchers.IO) {
            val min = java.util.concurrent.TimeUnit.MINUTES.toMillis(15)
            val interval = intervalMillis.coerceAtLeast(min)
            val imported = ImportedDao().queryByUUID(uuid) ?: return@withContext
            if (imported.type != Profile.Type.Url) return@withContext
            if (imported.interval == interval) return@withContext

            val updated = imported.copy(interval = interval)
            ImportedDao().update(updated)

            PendingDao().queryByUUID(uuid)?.let { pending ->
                PendingDao().update(pending.copy(interval = interval))
            }

            ProfileReceiver.cancelNext(context, imported)
            ProfileReceiver.scheduleNext(context, updated)
            context.sendProfileChanged(uuid)
        }
    }

    override suspend fun update(uuid: UUID, callback: IFetchObserver?) {
        // Per-uuid dedupe: drop the call if an update for this profile is
        // already in flight. The UI also blocks rapid taps via a modal
        // progress dialog, but this is the belt-and-suspenders guarantee:
        // any caller (auto-update WorkManager, notification action, AIDL
        // client) gets the same protection.
        if (!updatingUuids.add(uuid)) {
            Log.d("update($uuid) skipped — already in flight")
            return
        }
        try {
            val imported = ImportedDao().queryByUUID(uuid) ?: return
            if (imported.type != Profile.Type.Url) return

            // Direct invocation so the suspend point holds the lock for the
            // FULL fetch+verify duration. The previous implementation called
            // scheduleUpdate(true) which dispatched the work to ProfileWorker
            // (foreground service) and returned immediately — the lock would
            // have released after ~50ms while the real work ran in the
            // background, defeating the dedupe and letting a second tap kick
            // off a parallel fetch that fought the first one for the YAML
            // pipe. ProfileProcessor.update already holds its own
            // processLock so reentrancy from auto-update is safe.
            //
            // Side effect of bypassing ProfileWorker: the worker is what used
            // to broadcast Profile-Update-Completed / Failed (so observers
            // could surface a toast). We now emit those broadcasts here so
            // the UI keeps getting the same lifecycle events on every path.
            try {
                ProfileProcessor.update(context, uuid, callback)

                if (imported.source.startsWith("https://", true)) {
                    updateFlow(imported)
                }

                // Re-arm the timer-based auto-update from the new mtime so the
                // next automatic refresh fires at the correct interval — we no
                // longer go through scheduleUpdate() which used to do this for us.
                ImportedDao().queryByUUID(uuid)?.let {
                    ProfileReceiver.scheduleNext(context, it)
                }
                context.sendProfileUpdateCompleted(uuid)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    val reason = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                    context.sendProfileUpdateFailed(uuid, reason)
                }
                throw e
            }
        } finally {
            updatingUuids.remove(uuid)
        }
    }

    suspend fun updateFlow(old: Imported) {
        try {
            // ProfileProcessor.update just re-downloaded the subscription; the Go
            // fetch persisted the response headers next to config.yaml
            // (fetch-headers.json). Re-requesting the panel here — the old OkHttp
            // GET — doubled traffic (panels count fetches against quota), could
            // race the primary download, and kept a second header-decoding path
            // alive. Absent snapshot = no HTTP response observed (content:// or a
            // failed fetch) — same early-return the old `!response.isSuccessful`
            // gave us.
            val headers = FetchHeadersFile.readFrom(context.importedDir.resolve(old.uuid.toString()))
                ?: return

            // Operator brand parses independently of subscription-userinfo —
            // panels that don't carry quota (free tiers, custom auth flows)
            // still need to brand the app. We do this BEFORE the
            // subscription-userinfo early-return so brand survives even
            // when the panel stops sending quota headers.
            //
            // parse(), NOT parseHttpHeaders(): the snapshot values are already
            // correct UTF-8 (Go passes header bytes through and JSON preserves
            // them). parseHttpHeaders' ISO-8859-1→UTF-8 recovery is only for
            // live transports and would mangle non-ASCII (Cyrillic brand /
            // display names) coming from the file.
            val brand = com.github.kr328.clash.common.branding.BrandManifestParser.parse { key ->
                headers.get(key)
            }
            // confirmedResponse=true: the header snapshot only exists for a fetch
            // that completed AND passed engine validation (update() succeeded), so
            // an empty brand here is a real "served the sub with no X-Brand-*
            // headers" signal (not a transient failure) and feeds the debounced
            // auto-clear.
            BrandRefresh.apply(context, old.uuid, brand, confirmedResponse = true)

            // Re-derive the display name on EVERY update — not only when the panel sends
            // subscription-userinfo. Tying the rename to the quota header meant subscriptions
            // that don't send it never had their name corrected on update (the "белиберда /
            // URL-tail" name bug). Usage/expiry still come from subscription-userinfo when
            // present, but its absence must not skip the rename. (E-16)
            val usage = SubscriptionUsage.parse(headers.get("subscription-userinfo"))
            val renamed = deriveTitleFromHeaders(headers)
            val effectiveName = if (looksLikeGeneratedTokenName(old.name) && !renamed.isNullOrBlank()) {
                renamed
            } else {
                old.name
            }

            // Keep the previous quota/expiry when the panel didn't send subscription-userinfo
            // this time, instead of zeroing them.
            val new = Imported(
                old.uuid,
                effectiveName,
                old.type,
                old.source,
                old.interval,
                usage?.upload ?: old.upload,
                usage?.download ?: old.download,
                usage?.total ?: old.total,
                usage?.expireAt?.times(1000L) ?: old.expire,
                old.createdAt,
                old.profileOrder,
                ageSecretKey = old.ageSecretKey,
            )

            if (new != old) {
                ImportedDao().update(new)
                PendingDao().remove(new.uuid)
                context.sendProfileChanged(new.uuid)
            }
        } catch (e: Exception) {
            Log.w("updateFlow failed", e)
        }
    }

    // Delegate to the shared resolver so import and update decode headers identically (E-19):
    // title headers → Content-Disposition filename* (RFC-5987) → Subscription-Userinfo. Keep the
    // 64-char safety cap.
    private fun deriveTitleFromHeaders(headers: FetchHeadersFile): String? =
        SubscriptionNameGuesser.titleFromHeaders { key -> headers.get(key) }
            ?.let { if (it.length > 64) it.take(64) else it }

    private fun looksLikeGeneratedTokenName(name: String): Boolean {
        val n = name.trim()
        // Guard rails: only ever reconsider a medium-length single-word label — never rename a
        // human name with spaces/dots or a very short/long one.
        if (n.length !in 12..48) return false
        if (n.contains(' ') || n.contains('.') || n.contains('/')) return false
        // Shared predicate so import and update agree on what a token is (E-17): a name import
        // deliberately kept (e.g. all-lowercase "premiumplan1") is no longer replaced on update.
        return SubscriptionNameGuesser.looksLikeOpaqueToken(n)
    }

    override suspend fun commit(uuid: UUID, callback: IFetchObserver?) {
        ProfileProcessor.apply(context, uuid, callback)

        scheduleUpdate(uuid, false)
    }

    override suspend fun release(uuid: UUID) {
        ProfileProcessor.release(context, uuid)
    }

    override suspend fun delete(uuid: UUID) {
        ImportedDao().queryByUUID(uuid)?.also {
            ProfileReceiver.cancelNext(context, it)
        }

        ProfileProcessor.delete(context, uuid)
        BrandRefresh.onProfileDeleted(context, uuid)
        store.clearSubscriptionShareLinksLockedFor(uuid)
        invalidateSnapshotCache(uuid)
    }

    override suspend fun queryByUUID(uuid: UUID): Profile? {
        return resolveProfile(uuid)
    }

    override suspend fun queryAll(): List<Profile> {
        return withContext(Dispatchers.IO) {
            // Bulk-load both tables once instead of two per-uuid queries (N+1) inside the loop.
            val ordered = ImportedDao().queryAllOrderedUUIDs().map { it.uuid }.distinct()
            val importedByUuid = ImportedDao().queryAll().associateBy { it.uuid }
            val pendingByUuid = PendingDao().queryAll().associateBy { it.uuid }
            ordered.mapNotNull { uuid -> buildProfile(uuid, importedByUuid[uuid], pendingByUuid[uuid]) }
        }
    }

    override suspend fun reorder(uuids: List<String>) {
        withContext(Dispatchers.IO) {
            uuids.mapNotNull { raw ->
                runCatching { UUID.fromString(raw) }.getOrNull()
            }.distinct().forEachIndexed { index, uuid ->
                val order = index.toLong()
                ImportedDao().updateProfileOrder(uuid, order)
                PendingDao().updateProfileOrder(uuid, order)
            }
        }
    }

    override suspend fun queryActive(): Profile? {
        val active = store.activeProfile ?: return null

        return if (ImportedDao().exists(active)) {
            resolveProfile(active)
        } else {
            null
        }
    }

    override suspend fun setActive(profile: Profile) {
        ProfileProcessor.active(context, profile.uuid)
    }

    override suspend fun mergeRuleProviderYaml(
        uuid: UUID,
        ruleProvidersYaml: String,
        prependRuleLine: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) return@withContext false
            val ok = ruleApplyService.mergeProviderShortcut(uuid, ruleProvidersYaml, prependRuleLine)
            Log.d("mergeRuleProviderYaml ok=$ok")
            ok
        }
    }

    override suspend fun previewMergeRuleProviderYaml(
        uuid: UUID,
        ruleProvidersYaml: String,
        prependRuleLine: String,
    ): String? = previewRuleDryRun(uuid, "Rules", ruleApplyService.dryRunMergeProviderShortcut(uuid, ruleProvidersYaml, prependRuleLine))

    override suspend fun readProxyGroupsPreview(uuid: UUID): Map<String, ProxyGroupPreviewRow> {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext emptyMap()
            }
            val file = File(context.importedDir, "$uuid/config.yaml")
            if (!file.isFile || file.length() > PreviewResourceLimits.MAX_CONFIG_BYTES) {
                return@withContext emptyMap()
            }
            try {
                val snapshot = getOrParseSnapshot(uuid, file)
                // includeHidden = true: when the live engine path serves the
                // UI, the picker pills walk every group (visible + hidden) via
                // Clash.queryAllProxyGroupNamesIncludingHidden, so the offline
                // preview must match that universe — otherwise hidden auto
                // subgroups have no rows during the warmup race (live data not
                // ready yet, offline fallback missing the group), and the
                // expanded carriage flashes empty until proxyDetails arrives.
                ProxyGroupsYamlPreview.parseProxyGroupsPreview(
                    snapshot,
                    file.parentFile,
                    includeHidden = true,
                )
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

    override suspend fun readProxyTransports(uuid: UUID): Map<String, ProxyTransportInfo> {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext emptyMap()
            }
            val file = File(context.importedDir, "$uuid/config.yaml")
            if (!file.isFile || file.length() > PreviewResourceLimits.MAX_CONFIG_BYTES) {
                return@withContext emptyMap()
            }
            try {
                val snapshot = getOrParseSnapshot(uuid, file)
                ProxyTransportYamlPreview.parse(snapshot, file.parentFile)
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

    override suspend fun readRuleProvidersYaml(uuid: UUID): String? {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext null
            }
            val file = File(context.importedDir, "$uuid/config.yaml")
            if (!file.isFile) {
                return@withContext null
            }
            try {
                RuleProvidersYamlEdit.extractBlock(file.readText())
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun readConfigYaml(uuid: UUID): String? {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext null
            }
            val file = File(context.importedDir, "$uuid/config.yaml")
            if (!file.isFile) {
                return@withContext null
            }
            try {
                file.readText()
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun replaceRuleProvidersYaml(uuid: UUID, yaml: String): Boolean {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext false
            }
            val file = File(context.importedDir, "$uuid/config.yaml")
            if (!file.isFile) {
                return@withContext false
            }
            try {
                val merged = RuleProvidersYamlEdit.mergeIntoConfig(file.readText(), yaml)
                file.writeText(merged)
                // Keep structured repository synchronized with manual YAML edits.
                ruleApplyService.readStateJson(uuid)
                // Overlay: capture the user's rule-providers delta into the layer (survives updates).
                runCatching {
                    syncLayerFromConfig(uuid)
                    userLayerStore.update(uuid) { it.copy(ruleProviders = yaml) }
                }
                context.sendProfileChanged(uuid)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun previewReplaceRuleProvidersYaml(uuid: UUID, yaml: String): String? {
        return previewConfigMutation(
            uuid,
            "Rule providers",
            layerMutation = { it.copy(ruleProviders = yaml) },
        ) { current ->
            RuleProvidersYamlEdit.mergeIntoConfig(current, yaml)
        }
    }

    override suspend fun readProxyProvidersYaml(uuid: UUID): String? {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext null
            }
            val file = File(context.importedDir, "$uuid/config.yaml")
            if (!file.isFile) {
                return@withContext null
            }
            try {
                ProxyProvidersYamlEdit.extractBlock(file.readText())
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun replaceProxyProvidersYaml(uuid: UUID, yaml: String): Boolean {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext false
            }
            val file = File(context.importedDir, "$uuid/config.yaml")
            if (!file.isFile) {
                return@withContext false
            }
            try {
                val merged = ProxyProvidersYamlEdit.mergeIntoConfig(file.readText(), yaml)
                file.writeText(merged)
                // Overlay: capture the user's proxy-providers delta into the layer (survives updates).
                runCatching {
                    syncLayerFromConfig(uuid)
                    userLayerStore.update(uuid) { it.copy(proxyProviders = yaml) }
                }
                context.sendProfileChanged(uuid)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun previewReplaceProxyProvidersYaml(uuid: UUID, yaml: String): String? {
        return previewConfigMutation(
            uuid,
            "Proxy providers",
            layerMutation = { it.copy(proxyProviders = yaml) },
        ) { current ->
            ProxyProvidersYamlEdit.mergeIntoConfig(current, yaml)
        }
    }

    override suspend fun appendRelayProxyGroup(
        uuid: UUID,
        groupName: String,
        providerKeys: List<String>,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext false
            }
            val file = File(context.importedDir, "$uuid/config.yaml")
            if (!file.isFile) {
                return@withContext false
            }
            try {
                val text = file.readText()
                val merged = ProxyGroupsYamlEdit.appendSelectGroupUsingProviders(text, groupName, providerKeys)
                    ?: return@withContext false
                file.writeText(merged)
                // Overlay: capture the relay group so it survives subscription updates.
                runCatching {
                    syncLayerFromConfig(uuid)
                    userLayerStore.update(uuid) { l ->
                        l.copy(
                            relayGroups = l.relayGroups.filterNot { it.name == groupName } +
                                com.github.kr328.clash.service.util.RelayGroup(groupName, providerKeys),
                        )
                    }
                }
                context.sendProfileChanged(uuid)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun previewAppendRelayProxyGroup(
        uuid: UUID,
        groupName: String,
        providerKeys: List<String>,
    ): String? {
        return previewConfigMutation(
            uuid,
            "Proxy group",
            layerMutation = { l ->
                l.copy(
                    relayGroups = l.relayGroups.filterNot { it.name == groupName } +
                        com.github.kr328.clash.service.util.RelayGroup(groupName, providerKeys),
                )
            },
        ) { current ->
            ProxyGroupsYamlEdit.appendSelectGroupUsingProviders(current, groupName, providerKeys)
                ?: throw IllegalArgumentException("Proxy group already exists")
        }
    }

    override suspend fun removeProxyGroup(uuid: UUID, groupName: String): Boolean {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext false
            }
            val file = File(context.importedDir, "$uuid/config.yaml")
            if (!file.isFile) {
                return@withContext false
            }
            try {
                val merged = ProxyGroupsYamlEdit.removeGroupByName(file.readText(), groupName)
                    ?: return@withContext false
                file.writeText(merged)
                runCatching {
                    syncLayerFromConfig(uuid)
                    userLayerStore.update(uuid) { l -> l.copy(relayGroups = l.relayGroups.filterNot { it.name == groupName }) }
                }
                context.sendProfileChanged(uuid)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun previewRemoveProxyGroup(uuid: UUID, groupName: String): String? {
        return previewConfigMutation(
            uuid,
            "Proxy group",
            layerMutation = { l -> l.copy(relayGroups = l.relayGroups.filterNot { it.name == groupName }) },
        ) { current ->
            ProxyGroupsYamlEdit.removeGroupByName(current, groupName)
                ?: throw IllegalArgumentException("Proxy group was not found")
        }
    }

    override suspend fun setProxyDialerProxy(
        uuid: UUID,
        targetProxyName: String,
        dialerProxyName: String?,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext false
            }
            val dir = File(context.importedDir, uuid.toString())
            try {
                val ok = ProxyDialerYamlEdit.applyDialerProxy(dir, targetProxyName, dialerProxyName)
                if (ok) {
                    runCatching { syncLayerFromConfig(uuid) }
                    // E-01/E-05: record the proxy-chain intent directly in the user layer.
                    runCatching {
                        val dp = dialerProxyName?.takeIf { it.isNotBlank() }
                        userLayerStore.update(uuid) { l ->
                            l.copy(
                                proxyChain = if (dp != null) l.proxyChain + (targetProxyName to dp)
                                else l.proxyChain - targetProxyName,
                            )
                        }
                    }
                    context.sendProfileChanged(uuid)
                }
                ok
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun previewSetProxyDialerProxy(
        uuid: UUID,
        targetProxyName: String,
        dialerProxyName: String?,
    ): String? {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) return@withContext null
            val dir = File(context.importedDir, uuid.toString())
            try {
                val patch = ProxyDialerYamlEdit.previewDialerProxy(dir, targetProxyName, dialerProxyName)
                    ?: throw IllegalArgumentException("Proxy was not found")
                createPreview(
                    uuid = uuid,
                    title = "Proxy chain",
                    files = listOf(
                        filePreview(
                            dir,
                            patch.relativePath,
                            patch.currentYaml,
                            patch.proposedYaml,
                            patch.providerFile,
                        ),
                    ),
                    // E-01/E-05: the proxy-chain UI applies via preview->commit, so the chain intent
                    // must be recorded on the layer here (same {target: dialer} map compose expects),
                    // otherwise it's lost on recompose now that config.yaml extraction is gone.
                    layerMutation = { l ->
                        val dp = dialerProxyName?.takeIf { it.isNotBlank() }
                        l.copy(
                            proxyChain = if (dp != null) l.proxyChain + (targetProxyName to dp)
                            else l.proxyChain - targetProxyName,
                        )
                    },
                )
            } catch (e: Exception) {
                createInvalidPreview("Proxy chain", "", "", e)
            }
        }
    }

    override suspend fun listProxyDialerChains(uuid: UUID): List<String> {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext emptyList()
            }
            val dir = File(context.importedDir, uuid.toString())
            try {
                ProxyDialerYamlEdit.listDialerChains(dir).map { row ->
                    listOf(row.targetName, row.dialerName, row.relativePath).joinToString("\u001F")
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    override suspend fun clearAllProxyDialerChains(uuid: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext false
            }
            val dir = File(context.importedDir, uuid.toString())
            try {
                val ok = ProxyDialerYamlEdit.clearAllDialerProxies(dir)
                if (ok) {
                    // E-01/E-05: clear the proxy-chain intent in the user layer too.
                    runCatching { userLayerStore.update(uuid) { it.copy(proxyChain = emptyMap()) } }
                    context.sendProfileChanged(uuid)
                }
                ok
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun previewClearAllProxyDialerChains(uuid: UUID): String? {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) return@withContext null
            val dir = File(context.importedDir, uuid.toString())
            try {
                val patches = ProxyDialerYamlEdit.previewClearAllDialerProxies(dir)
                if (patches.isEmpty()) throw IllegalArgumentException("No saved proxy chains")
                createPreview(
                    uuid = uuid,
                    title = "Proxy chains",
                    files = patches.map {
                        filePreview(dir, it.relativePath, it.currentYaml, it.proposedYaml, it.providerFile)
                    },
                    // E-01/E-05: clearing all chains must clear the layer intent too, else compose
                    // re-applies them from the store on recompose.
                    layerMutation = { it.copy(proxyChain = emptyMap()) },
                )
            } catch (e: Exception) {
                createInvalidPreview("Proxy chains", "", "", e)
            }
        }
    }

    override suspend fun readProxyProviderLabelsJson(uuid: UUID): String? {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) return@withContext null
            val f = File(context.importedDir, "$uuid/proxy_providers_labels.json")
            if (!f.isFile) return@withContext null
            try {
                f.readText()
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun writeProxyProviderLabelsJson(uuid: UUID, json: String): Boolean {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) return@withContext false
            try {
                File(context.importedDir, "$uuid/proxy_providers_labels.json").writeText(json)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun readRuleState(uuid: UUID): String? {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) return@withContext null
            ruleApplyService.readStateJson(uuid)
        }
    }

    override suspend fun applyRuleState(uuid: UUID, stateJson: String): Boolean {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) return@withContext false
            val ok = ruleApplyService.applyStateJson(uuid, stateJson)
            if (ok) runCatching {
                syncLayerFromConfig(uuid)
                // E-01/E-05: mirror the saved (normalized) rule state into the user layer directly,
                // so the layer holds explicit rule intent instead of it being re-derived on sync.
                ruleApplyService.readStateJson(uuid)?.let { saved ->
                    userLayerStore.update(uuid) { it.copy(rules = ruleApplyService.parseState(saved)) }
                }
            }
            Log.d("applyRuleState ok=$ok")
            ok
        }
    }


    override suspend fun queryDnsHostsConfigJson(uuid: UUID): String? {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) return@withContext null
            val dir = File(context.importedDir, uuid.toString())
            if (!File(dir, "config.yaml").isFile) return@withContext null
            val snapshot = runCatching { Clash.parseProfileSnapshot(dir) }.getOrNull()
                ?: return@withContext null
            val config = DnsHostsConfig.fromSnapshot(snapshot)
            previewJson.encodeToString(DnsHostsConfig.serializer(), config)
        }
    }

    override suspend fun previewSetDnsHosts(uuid: UUID, configJson: String): String? {
        val config = previewJson.decodeFromString(DnsHostsConfig.serializer(), configJson)
        // E-01/E-05: write the DNS/hosts intent to the user layer directly (explicit intent), the
        // same way rule-/proxy-providers already do, instead of relying on config.yaml extraction.
        return previewConfigMutation(
            uuid,
            "DNS & Hosts",
            layerMutation = { it.copy(dnsHosts = config) },
        ) { current ->
            DnsHostsYamlEdit.render(current, config)
        }
    }

    override suspend fun isDnsHostsManaged(uuid: UUID): Boolean {
        return withContext(Dispatchers.IO) { store.isDnsHostsManaged(uuid) }
    }

    override suspend fun setDnsHostsManaged(uuid: UUID, managed: Boolean) {
        withContext(Dispatchers.IO) { store.setDnsHostsManaged(uuid, managed) }
    }

    override suspend fun queryTunnelsConfigJson(uuid: UUID): String? {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) return@withContext null
            val dir = File(context.importedDir, uuid.toString())
            if (!File(dir, "config.yaml").isFile) return@withContext null
            val snapshot = runCatching { Clash.parseProfileSnapshot(dir) }.getOrNull()
                ?: return@withContext null
            val config = TunnelsConfig.fromSnapshot(snapshot)
            previewJson.encodeToString(TunnelsConfig.serializer(), config)
        }
    }

    override suspend fun previewSetTunnels(uuid: UUID, configJson: String): String? {
        val config = previewJson.decodeFromString(TunnelsConfig.serializer(), configJson)
        // E-01/E-05: write the tunnels intent to the user layer directly (explicit intent).
        return previewConfigMutation(
            uuid,
            "Tunnels",
            layerMutation = { it.copy(tunnels = config) },
        ) { current ->
            TunnelsYamlEdit.render(current, config)
        }
    }

    override suspend fun isTunnelsManaged(uuid: UUID): Boolean {
        return withContext(Dispatchers.IO) { store.isTunnelsManaged(uuid) }
    }

    override suspend fun setTunnelsManaged(uuid: UUID, managed: Boolean) {
        withContext(Dispatchers.IO) { store.setTunnelsManaged(uuid, managed) }
    }

    override suspend fun previewRuleStateYaml(uuid: UUID, stateJson: String): String? =
        withContext(Dispatchers.IO) {
            RuleApplyService(context).dryRunStateJson(uuid, stateJson)?.proposedYaml
        }

    override suspend fun readRuleEditorBundle(uuid: UUID): String? =
        withContext(Dispatchers.IO) { RuleApplyService(context).readEditorBundle(uuid) }

    override suspend fun applyYamlPreview(previewId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val cached = synchronized(previewCache) { previewCache.remove(previewId) } ?: return@withContext false
            if (ImportedDao().queryByUUID(cached.uuid) == null) return@withContext false
            val dir = File(context.importedDir, cached.uuid.toString())

            // Read + verify hashes for every file before touching anything on disk, and
            // keep the originals in memory so we can roll a partially-written batch back
            // if a later write fails. The actual write uses tmp + rename per file so a
            // single-file write is atomic on the local FS even if the process is killed
            // mid-flight.
            val originals = LinkedHashMap<File, String>()
            val targets = LinkedHashMap<CachedFile, File>()
            for (file in cached.files) {
                val target = resolveCachedFile(dir, file) ?: return@withContext false
                if (!target.isFile) return@withContext false
                val current = target.readText()
                if (YamlPreviewSupport.sha256(current) != file.sourceHash) return@withContext false
                originals[target] = current
                targets[file] = target
            }

            val written = ArrayList<File>(cached.files.size)
            try {
                for (file in cached.files) {
                    val target = targets.getValue(file)
                    atomicWriteText(target, file.proposedYaml)
                    written += target
                }
                cached.ruleStateJson?.let {
                    ruleApplyService.saveStateJson(cached.uuid, it)
                }
                // Overlay: keep the user layer in sync with the just-committed config.yaml so the
                // edit survives the next subscription update (config-overlay-architecture). The
                // clean sections are re-derived from config.yaml; raw provider edits carry an
                // explicit layer mutation (their user delta can't be re-extracted from config.yaml).
                runCatching { syncLayerFromConfig(cached.uuid) }
                cached.layerMutation?.let { mut -> runCatching { userLayerStore.update(cached.uuid, mut) } }
                context.sendProfileChanged(cached.uuid)
                true
            } catch (e: Exception) {
                Log.w("applyYamlPreview write failed, rolling back ${written.size} file(s)", e)
                written.forEach { target ->
                    runCatching { originals[target]?.let { atomicWriteText(target, it) } }
                }
                false
            }
        }
    }

    private fun atomicWriteText(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.applying")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw java.io.IOException("Failed to rename ${tmp.name} to ${target.name}")
        }
    }

    override suspend fun readProxyEntryYaml(uuid: UUID, proxyName: String): String? {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext null
            }
            val file = File(context.importedDir, "$uuid/config.yaml")
            if (!file.isFile) {
                return@withContext null
            }
            try {
                val snapshot = getOrParseSnapshot(uuid, file)
                ProxyYamlPreview.extractProxyEntry(snapshot, proxyName)
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun rememberProxySelection(uuid: UUID, group: String, name: String) {
        withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) return@withContext
            SelectionDao().setSelected(Selection(uuid, group, name))
        }
    }

    override suspend fun queryProxySelections(uuid: UUID): Map<String, String> {
        return withContext(Dispatchers.IO) {
            SelectionDao().querySelections(uuid).associate { it.proxy to it.selected }
        }
    }

    override suspend fun readImportedConfigYaml(uuid: UUID): String? {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext null
            }
            val file = File(context.importedDir, "$uuid/config.yaml")
            if (!file.isFile) {
                return@withContext null
            }
            try {
                file.readText()
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun readBrandJsonFor(uuid: UUID): String? = withContext(Dispatchers.IO) {
        val store = BrandStore(context)
        val manifest = store.manifestFor(uuid)
        // Visual brand needs isActiveFor (enabled + identity). Operator POLICY flags (hideGlobalMode)
        // apply without branding, so surface the manifest when it carries policy too — otherwise the
        // UI never sees the flag and the restriction silently does nothing.
        if (store.isActiveFor(uuid) || manifest.hasPolicy()) manifest.toJson() else null
    }

    override suspend fun brandLogoPathFor(uuid: UUID, darkTheme: Boolean): String? = withContext(Dispatchers.IO) {
        val store = BrandStore(context)
        if (!store.isActiveFor(uuid)) return@withContext null
        store.logoPathFor(uuid, darkTheme)?.takeIf { File(it).isFile }
    }

    private suspend fun previewRuleDryRun(
        uuid: UUID,
        title: String,
        dryRun: RuleApplyService.RuleDryRun?,
    ): String? {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null || dryRun == null) return@withContext null
            try {
                createPreview(
                    uuid = uuid,
                    title = title,
                    files = listOf(
                        CachedFile(
                            relativePath = "config.yaml",
                            sourceHash = YamlPreviewSupport.sha256(dryRun.currentYaml),
                            proposedYaml = dryRun.proposedYaml,
                        )
                    ),
                    currentYaml = dryRun.currentYaml,
                    proposedYaml = dryRun.proposedYaml,
                    ruleStateJson = previewJson.encodeToString(RuleState.serializer(), dryRun.normalizedState),
                )
            } catch (e: Exception) {
                createInvalidPreview(title, "", "", e)
            }
        }
    }

    private suspend fun previewConfigMutation(
        uuid: UUID,
        title: String,
        layerMutation: ((com.github.kr328.clash.service.util.UserLayer) -> com.github.kr328.clash.service.util.UserLayer)? = null,
        mutate: (String) -> String,
    ): String? {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) return@withContext null
            val dir = File(context.importedDir, uuid.toString())
            val file = File(dir, "config.yaml")
            if (!file.isFile) return@withContext null
            val current = file.readText()
            try {
                val proposed = mutate(current)
                YamlPreviewSupport.validateConfigYaml(proposed)
                createPreview(
                    uuid = uuid,
                    title = title,
                    files = listOf(filePreview(dir, "config.yaml", current, proposed)),
                    currentYaml = current,
                    proposedYaml = proposed,
                    layerMutation = layerMutation,
                )
            } catch (e: Exception) {
                createInvalidPreview(title, current, current, e)
            }
        }
    }

    private fun filePreview(
        dir: File,
        relativePath: String,
        current: String,
        proposed: String,
        providerFile: Boolean = false,
    ): CachedFile {
        YamlPreviewSupport.validateConfigYaml(proposed)
        val cached = CachedFile(
            relativePath = relativePath,
            sourceHash = YamlPreviewSupport.sha256(current),
            proposedYaml = proposed,
            providerFile = providerFile,
        )
        require(resolveCachedFile(dir, cached) != null) { "Unsafe preview file path" }
        return cached
    }

    private fun resolveCachedFile(dir: File, file: CachedFile): File? {
        if (file.providerFile) return ProxyDialerYamlEdit.resolveProviderPath(dir, file.relativePath)
        if (file.relativePath != "config.yaml") return null
        return runCatching {
            val profileRoot = dir.canonicalFile
            File(profileRoot, "config.yaml").canonicalFile.takeIf { it.parentFile == profileRoot }
        }.getOrNull()
    }

    private fun createPreview(
        uuid: UUID,
        title: String,
        files: List<CachedFile>,
        currentYaml: String = joinPreviewFiles(
            files,
            File(context.importedDir, uuid.toString()),
            useProposed = false,
        ),
        proposedYaml: String = joinPreviewFiles(
            files,
            File(context.importedDir, uuid.toString()),
            useProposed = true,
        ),
        ruleStateJson: String? = null,
        layerMutation: ((com.github.kr328.clash.service.util.UserLayer) -> com.github.kr328.clash.service.util.UserLayer)? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        synchronized(previewCache) {
            previewCache[id] = CachedPreview(uuid, files, ruleStateJson, layerMutation)
            while (previewCache.size > 16) {
                previewCache.remove(previewCache.keys.first())
            }
        }
        return previewJson.encodeToString(
            YamlPreview(
                id = id,
                title = title,
                currentYaml = YamlPreviewSupport.boundedPreviewText(currentYaml),
                proposedYaml = YamlPreviewSupport.boundedPreviewText(proposedYaml),
                diff = YamlPreviewSupport.unifiedDiff(currentYaml, proposedYaml),
                valid = true,
            )
        )
    }

    private fun createInvalidPreview(title: String, current: String, proposed: String, error: Throwable): String {
        return previewJson.encodeToString(
            YamlPreview(
                id = "",
                title = title,
                currentYaml = YamlPreviewSupport.boundedPreviewText(current),
                proposedYaml = YamlPreviewSupport.boundedPreviewText(proposed),
                diff = YamlPreviewSupport.unifiedDiff(current, proposed),
                valid = false,
                error = error.message ?: error.toString(),
            )
        )
    }

    private fun joinPreviewFiles(files: List<CachedFile>, dir: File, useProposed: Boolean): String {
        return files.joinToString("\n\n") { file ->
            val body = if (useProposed) {
                file.proposedYaml
            } else {
                requireNotNull(resolveCachedFile(dir, file)) { "Unsafe preview file path" }.readText()
            }
            "# ${file.relativePath}\n$body"
        }
    }

    private suspend fun resolveProfile(uuid: UUID): Profile? {
        val imported = ImportedDao().queryByUUID(uuid)
        val pending = PendingDao().queryByUUID(uuid)
        return buildProfile(uuid, imported, pending)
    }

    /**
     * Merge the imported + pending rows for [uuid] into a [Profile]. Pure (no DB) so callers that
     * already loaded the rows in bulk (see [queryAll]) avoid an N+1 of per-uuid queries.
     */
    private fun buildProfile(uuid: UUID, imported: Imported?, pending: Pending?): Profile? {
        val active = store.activeProfile
        val name = pending?.name ?: imported?.name ?: return null
        val type = pending?.type ?: imported?.type ?: return null
        val source = pending?.source ?: imported?.source ?: return null
        val interval = pending?.interval ?: imported?.interval ?: return null
        val upload = pending?.upload ?: imported?.upload ?: return null
        val download = pending?.download ?: imported?.download ?: return null
        val total = pending?.total ?: imported?.total ?: return null
        val expire = pending?.expire ?: imported?.expire ?: return null

        return Profile(
            uuid,
            name,
            type,
            source,
            active != null && imported?.uuid == active,
            interval,
            upload,
            download,
            total,
            expire,
            resolveUpdatedAt(uuid),
            imported != null,
            pending != null,
            ageSecretKey = pending?.ageSecretKey ?: imported?.ageSecretKey,
        )
    }

    private fun resolveUpdatedAt(uuid: UUID): Long {
        return context.pendingDir.resolve(uuid.toString()).directoryLastModified
            ?: context.importedDir.resolve(uuid.toString()).directoryLastModified
            ?: -1
    }

    private suspend fun nextProfileOrder(): Long {
        val importedMax = ImportedDao().queryMaxProfileOrder() ?: -1L
        val pendingMax = PendingDao().queryMaxProfileOrder() ?: -1L
        return maxOf(importedMax, pendingMax) + 1L
    }

    private fun cloneImportedFiles(source: UUID, target: UUID = source) {
        val s = context.importedDir.resolve(source.toString())
        val t = context.pendingDir.resolve(target.toString())

        if (!s.exists())
            throw FileNotFoundException("profile $source not found")

        t.deleteRecursively()

        s.copyRecursively(t)
    }

    private suspend fun scheduleUpdate(uuid: UUID, startImmediately: Boolean) {
        val imported = ImportedDao().queryByUUID(uuid) ?: return

        if (startImmediately) {
            ProfileReceiver.schedule(context, imported)
        } else {
            ProfileReceiver.scheduleNext(context, imported)
        }
    }
}
