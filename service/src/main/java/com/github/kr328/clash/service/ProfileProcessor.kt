package com.github.kr328.clash.service

import android.content.Context
import android.net.Uri
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.SubscriptionUsage
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Pending
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.GeoUrlSanitizer
import com.github.kr328.clash.service.util.MihomoConfigDocument
import com.github.kr328.clash.service.util.RuleApplyService
import com.github.kr328.clash.service.util.RuleMapper
import com.github.kr328.clash.service.util.FetchErrorClassifier
import com.github.kr328.clash.service.util.MergeEngineVerdict
import com.github.kr328.clash.service.util.ConfigComposer
import com.github.kr328.clash.service.util.GeoDataSources
import com.github.kr328.clash.service.util.ProfileComposer
import com.github.kr328.clash.service.util.ProfileMigration
import com.github.kr328.clash.service.util.UserLayerStore
import com.github.kr328.clash.service.util.YamlHardener
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.processingDir
import com.github.kr328.clash.common.util.ShareImportSupport
import com.github.kr328.clash.common.util.SubscriptionOverrides
import com.github.kr328.clash.common.util.SubscriptionRequestHeaders
import com.github.kr328.clash.service.util.sendProfileChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit


object ProfileProcessor {
    private val profileLock = Mutex()
    private val processLock = Mutex()

    suspend fun apply(context: Context, uuid: UUID, callback: IFetchObserver? = null) {
        withContext(Dispatchers.IO) {
            processLock.withLock {
                val snapshot = profileLock.withLock {
                    val pending = PendingDao().queryByUUID(uuid)
                        ?: throw IllegalArgumentException("profile $uuid not found")

                    pending.enforceFieldValid()

                    context.processingDir.deleteRecursively()
                    context.processingDir.mkdirs()

                    context.pendingDir.resolve(pending.uuid.toString())
                        .copyRecursively(context.processingDir, overwrite = true)

                    pending
                }

                val force = snapshot.type != Profile.Type.File
                var cb = callback

                val userAgentOverride = SubscriptionOverrides.getUserAgent(context, snapshot.uuid)
                val strictUserAgent = SubscriptionOverrides.isStrictUserAgent(context, snapshot.uuid)
                try {
                    Clash.fetchAndValid(
                        context.processingDir,
                        snapshot.source,
                        force,
                        SubscriptionRequestHeaders.toNativeFetchJson(context, userAgentOverride),
                    ) {
                        try {
                            cb?.updateStatus(it)
                        } catch (e: Exception) {
                            cb = null

                            Log.w("Report fetch status callback failed", e)
                        }
                    }.await()
                } catch (e: Exception) {
                    if (userAgentOverride.isNullOrBlank() || strictUserAgent) {
                        throw FetchErrorClassifier.clarify(context.processingDir, e)
                    }

                    Log.w("Subscription fetch failed with custom User-Agent, retrying default core User-Agent", e)
                    Clash.fetchAndValid(
                        context.processingDir,
                        snapshot.source,
                        force,
                        SubscriptionRequestHeaders.toNativeFetchJson(context, null),
                    ) {
                        try {
                            cb?.updateStatus(it)
                        } catch (e2: Exception) {
                            cb = null
                            Log.w("Report fetch status callback failed", e2)
                        }
                    }.await()
                }

                GeoUrlSanitizer.sanitizeProfile(context.processingDir)
                YamlHardener.hardenProfile(
                    context.processingDir,
                    ServiceStore(context).proxyHardeningMode,
                )
                // Overlay (config-overlay-architecture): record the imported config as the
                // subscription base so future updates / VPN starts can compose the user layer on top.
                runCatching {
                    val cfg = File(context.processingDir, "config.yaml")
                    if (cfg.isFile) {
                        File(context.processingDir, ProfileComposer.SUBSCRIPTION_FILE).writeText(cfg.readText())
                    }
                }

                withContext(NonCancellable) {
                    profileLock.withLock {
                        if (PendingDao().queryByUUID(snapshot.uuid) != snapshot) return@withLock
                        context.importedDir.resolve(snapshot.uuid.toString())
                            .deleteRecursively()
                        context.processingDir
                            .copyRecursively(context.importedDir.resolve(snapshot.uuid.toString()))

                        val old = ImportedDao().queryByUUID(snapshot.uuid)
                        var upload: Long = 0
                        var download: Long = 0
                        var total: Long = 0
                        var expire: Long = 0
                        if (snapshot?.type == Profile.Type.Url) {
                            if (snapshot.source.startsWith("https://", true)) {
                                val client = OkHttpClient()
                                val request = Request.Builder()
                                    .url(snapshot.source)
                                    .apply {
                                        SubscriptionRequestHeaders.build(
                                            context,
                                            SubscriptionOverrides.getUserAgent(context, snapshot.uuid),
                                        ).forEach { (k, v) ->
                                            header(k, v)
                                        }
                                    }
                                    .build()

                                client.newCall(request).execute().use { response ->
                                    val usage = SubscriptionUsage.parse(response.headers["subscription-userinfo"])
                                    upload = usage?.upload ?: 0L
                                    download = usage?.download ?: 0L
                                    total = usage?.total ?: 0L
                                    expire = usage?.expireAt?.times(1000L) ?: 0L
                                }
                            }
                            val new = Imported(
                                snapshot.uuid,
                                snapshot.name,
                                snapshot.type,
                                snapshot.source,
                                snapshot.interval,
                                upload,
                                download,
                                total,
                                expire,
                                old?.createdAt ?: System.currentTimeMillis(),
                                old?.profileOrder ?: snapshot.profileOrder,
                            )
                            if (old != null) {
                                ImportedDao().update(new)
                            } else {
                                ImportedDao().insert(new)
                            }

                            PendingDao().remove(snapshot.uuid)

                            context.pendingDir.resolve(snapshot.uuid.toString())
                                .deleteRecursively()

                            context.sendProfileChanged(snapshot.uuid)
                        } else if (snapshot?.type == Profile.Type.File) {
                            val new = Imported(
                                snapshot.uuid,
                                snapshot.name,
                                snapshot.type,
                                snapshot.source,
                                snapshot.interval,
                                upload,
                                download,
                                total,
                                expire,
                                old?.createdAt ?: System.currentTimeMillis(),
                                old?.profileOrder ?: snapshot.profileOrder,
                            )
                            if (old != null) {
                                ImportedDao().update(new)
                            } else {
                                ImportedDao().insert(new)
                            }

                            PendingDao().remove(snapshot.uuid)

                            context.pendingDir.resolve(snapshot.uuid.toString())
                                .deleteRecursively()

                            context.sendProfileChanged(snapshot.uuid)
                        }
                    }
                }
            }
        }
    }

    suspend fun update(context: Context, uuid: UUID, callback: IFetchObserver?) {
        withContext(Dispatchers.IO) {
            processLock.withLock {
                val snapshot = profileLock.withLock {
                    val imported = ImportedDao().queryByUUID(uuid)
                        ?: throw IllegalArgumentException("profile $uuid not found")

                    context.processingDir.deleteRecursively()
                    context.processingDir.mkdirs()

                    context.importedDir.resolve(imported.uuid.toString())
                        .copyRecursively(context.processingDir, overwrite = true)

                    imported
                }

                val configFile = File(context.processingDir, "config.yaml")
                val serviceStore = ServiceStore(context)
                val dnsHostsManaged = serviceStore.isDnsHostsManaged(snapshot.uuid)
                val tunnelsManaged = serviceStore.isTunnelsManaged(snapshot.uuid)
                // Overlay (config-overlay-architecture): capture the user's edit layer from the
                // CURRENT (pre-fetch) config — before the fetch overwrites it — so it can be composed
                // onto the freshly fetched subscription below. Replaces SubscriptionUpdateMerge.
                val capturedLayer = if (configFile.isFile) {
                    ProfileMigration.buildLayerFromConfig(
                        profileDir = context.processingDir,
                        rulesStateJson = File(context.processingDir, "rules_state.json").takeIf { it.isFile }
                            ?.let { runCatching { it.readText() }.getOrNull() },
                        dnsHostsManaged = dnsHostsManaged,
                        tunnelsManaged = tunnelsManaged,
                        parseSnapshot = { d -> runCatching { Clash.parseProfileSnapshot(d) }.getOrNull() },
                        base = UserLayerStore.loadAt(context.processingDir),
                    )
                } else {
                    UserLayerStore.loadAt(context.processingDir)
                }

                var cb = callback

                val userAgentOverride = SubscriptionOverrides.getUserAgent(context, snapshot.uuid)
                val strictUserAgent = SubscriptionOverrides.isStrictUserAgent(context, snapshot.uuid)
                var effectiveUserAgentOverride = userAgentOverride
                try {
                    Clash.fetchAndValid(
                        context.processingDir,
                        snapshot.source,
                        true,
                        SubscriptionRequestHeaders.toNativeFetchJson(context, userAgentOverride),
                    ) {
                        try {
                            cb?.updateStatus(it)
                        } catch (e: Exception) {
                            cb = null

                            Log.w("Report fetch status callback failed", e)
                        }
                    }.await()
                } catch (e: Exception) {
                    if (userAgentOverride.isNullOrBlank() || strictUserAgent) {
                        throw FetchErrorClassifier.clarify(context.processingDir, e)
                    }

                    Log.w("Subscription update failed with custom User-Agent, retrying default core User-Agent", e)
                    Clash.fetchAndValid(
                        context.processingDir,
                        snapshot.source,
                        true,
                        SubscriptionRequestHeaders.toNativeFetchJson(context, null),
                    ) {
                        try {
                            cb?.updateStatus(it)
                        } catch (e2: Exception) {
                            cb = null
                            Log.w("Report fetch status callback failed", e2)
                        }
                    }.await()
                    effectiveUserAgentOverride = null
                }

                // Clear any stale warnings from a previous update; set below only
                // if THIS merge breaks a config / drops an orphaned rule.
                ServiceStore(context).setUpdateEngineWarning(snapshot.uuid, false)
                ServiceStore(context).setOrphanedRulesDropped(snapshot.uuid, emptyList())
                if (configFile.isFile) {
                    val fetchedText = configFile.readText()
                    // The freshly fetched subscription is the new canonical base; persist it and
                    // compose the captured user layer on top (Clash-Verge-Rev style overlay).
                    File(context.processingDir, ProfileComposer.SUBSCRIPTION_FILE).writeText(fetchedText)
                    UserLayerStore.saveAt(context.processingDir, capturedLayer)
                    val geoUrls = GeoDataSources.resolve(
                        preset = serviceStore.geoDataSourcePreset,
                        customGeoIp = serviceStore.geoDataCustomGeoIp,
                        customGeoSite = serviceStore.geoDataCustomGeoSite,
                        customMmdb = serviceStore.geoDataCustomMmdb,
                        customAsn = serviceStore.geoDataCustomAsn,
                    )
                    val composed = ConfigComposer.compose(
                        fetchedText, capturedLayer, geoUrls, serviceStore.proxyHardeningMode,
                    )
                    // Runtime engine gate (§config-engine-gate): NEVER apply a config the engine
                    // rejects. If our overlay broke an otherwise-valid subscription, fall back to the
                    // clean fetched subscription so the update still works, and surface that the local
                    // edits could not be applied. PreexistingBroken uses the fetched body as-is.
                    val composedError = Clash.validateProfileBytes(composed)
                    val verdict = if (composedError == null) {
                        MergeEngineVerdict.Ok
                    } else {
                        MergeEngineVerdict.classify(Clash.validateProfileBytes(fetchedText), composedError)
                    }
                    when (verdict) {
                        MergeEngineVerdict.MergeIntroduced -> {
                            Log.w("Overlay broke a valid subscription for ${snapshot.uuid}; applying clean fetched instead. $composedError")
                            serviceStore.setUpdateEngineWarning(snapshot.uuid, true)
                        }
                        MergeEngineVerdict.PreexistingBroken ->
                            Log.w("Composed config invalid for ${snapshot.uuid}, but fetched was already invalid (using fetched): $composedError")
                        MergeEngineVerdict.Ok -> Unit
                    }
                    configFile.writeText(if (verdict.appliesMergedConfig()) composed else fetchedText)
                    Log.d("Subscription overlay composed: user layer re-applied onto fresh subscription for ${snapshot.uuid}")

                    // Subscription providers are already on disk from the fetch above; this pass only
                    // downloads local-only providers reintroduced by the layer (force=false).
                    Clash.fetchProvidersAndValid(
                        context.processingDir,
                        false,
                        SubscriptionRequestHeaders.toNativeFetchJson(context, effectiveUserAgentOverride),
                    ) {
                        try {
                            cb?.updateStatus(it)
                        } catch (e: Exception) {
                            cb = null
                            Log.w("Report provider refresh status callback failed", e)
                        }
                    }.await()
                }

                // Tier-2 rule reconciliation is no longer needed: rules now come from the user layer
                // (RuleMapper.composeUserRulesOnto prepends them onto the fresh subscription), so
                // there is no string-merge classification to repair.

                GeoUrlSanitizer.sanitizeProfile(context.processingDir)
                // Hardening also runs inside ConfigComposer.compose above; this is a defensive
                // second pass over the whole processing dir (provider files included) and is
                // idempotent.
                YamlHardener.hardenProfile(
                    context.processingDir,
                    serviceStore.proxyHardeningMode,
                )

                withContext(NonCancellable) {
                    profileLock.withLock {
                        if (ImportedDao().exists(snapshot.uuid)) {
                            context.importedDir.resolve(snapshot.uuid.toString()).deleteRecursively()
                            context.processingDir
                                .copyRecursively(context.importedDir.resolve(snapshot.uuid.toString()))

                            context.sendProfileChanged(snapshot.uuid)
                        }
                    }
                }
            }
        }
    }

    suspend fun delete(context: Context, uuid: UUID) {
        withContext(NonCancellable) {
            profileLock.withLock {
                ImportedDao().remove(uuid)
                PendingDao().remove(uuid)

                val pending = context.pendingDir.resolve(uuid.toString())
                val imported = context.importedDir.resolve(uuid.toString())

                pending.deleteRecursively()
                imported.deleteRecursively()

                context.sendProfileChanged(uuid)
            }
        }
    }

    suspend fun release(context: Context, uuid: UUID): Boolean {
        return withContext(NonCancellable) {
            profileLock.withLock {
                PendingDao().remove(uuid)

                context.pendingDir.resolve(uuid.toString()).deleteRecursively()
            }
        }
    }

    suspend fun active(context: Context, uuid: UUID) {
        withContext(NonCancellable) {
            profileLock.withLock {
                if (ImportedDao().exists(uuid)) {
                    val store = ServiceStore(context)

                    store.activeProfile = uuid

                    context.sendProfileChanged(uuid)
                }
            }
        }
    }

    private fun Pending.enforceFieldValid() {
        when {
            name.isBlank() ->
                throw IllegalArgumentException("Empty name")

            source.isEmpty() && type != Profile.Type.File ->
                throw IllegalArgumentException("Invalid url")

            source.isNotEmpty() && type == Profile.Type.Url &&
                !ShareImportSupport.isAllowedUrlProfileSource(source) ->
                throw IllegalArgumentException("Unsupported url $source")

            source.isNotEmpty() && type == Profile.Type.External -> {
                val scheme = Uri.parse(source).scheme?.lowercase(Locale.getDefault())
                if (scheme != "https" && scheme != "http" && scheme != "content") {
                    throw IllegalArgumentException("Unsupported url $source")
                }
            }

            interval != 0L && TimeUnit.MILLISECONDS.toMinutes(interval) < 15 ->
                throw IllegalArgumentException("Invalid interval")
        }
    }
}
