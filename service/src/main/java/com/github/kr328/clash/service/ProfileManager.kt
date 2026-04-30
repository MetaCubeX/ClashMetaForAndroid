package com.github.kr328.clash.service

import android.content.Context
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.MaybeBase64
import com.github.kr328.clash.common.util.SubscriptionOverrides
import com.github.kr328.clash.common.util.SubscriptionRequestHeaders
import com.github.kr328.clash.common.util.SubscriptionUsage
import com.github.kr328.clash.service.data.Database
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Pending
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.data.Selection
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.service.remote.IProfileManager
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.directoryLastModified
import com.github.kr328.clash.service.util.generateProfileUUID
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.ProxyGroupsYamlPreview
import com.github.kr328.clash.service.util.ProxyYamlPreview
import com.github.kr328.clash.service.util.RuleApplyService
import com.github.kr328.clash.service.util.ProxyDialerYamlEdit
import com.github.kr328.clash.service.util.ProxyGroupsYamlEdit
import com.github.kr328.clash.service.util.ProxyProvidersYamlEdit
import com.github.kr328.clash.service.util.RuleProvidersYamlEdit
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.sendProfileChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.util.*

class ProfileManager(private val context: Context) : IProfileManager,
    CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val store = ServiceStore(context)
    private val ruleApplyService = RuleApplyService(context)

    init {
        launch {
            Database.database //.init

            ProfileReceiver.rescheduleAll(context)
        }
    }

    override suspend fun create(type: Profile.Type, name: String, source: String): UUID {
        val uuid = generateProfileUUID()
        val pending = Pending(
            uuid = uuid,
            name = name,
            type = type,
            source = source,
            interval = 0,
            upload = 0,
            total = 0,
            download = 0,
            expire = 0,
        )

        PendingDao().insert(pending)

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
        )

        cloneImportedFiles(uuid, newUUID)

        PendingDao().insert(pending)

        return newUUID
    }

    override suspend fun patch(uuid: UUID, name: String, source: String, interval: Long) {
        val locked = store.subscriptionShareLinksLocked
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
            )

            PendingDao().update(newPending)
        }
    }

    override suspend fun update(uuid: UUID) {
        scheduleUpdate(uuid, true)
        ImportedDao().queryByUUID(uuid)?.let {
            if (it.type == Profile.Type.Url && it.source.startsWith("https://",true)) {
                updateFlow(it)
            }
        }
    }

    suspend fun updateFlow(old: Imported) {
        val client = OkHttpClient()
        try {
            val request = Request.Builder()
                .url(old.source)
                .apply {
                    SubscriptionRequestHeaders.build(
                        context,
                        SubscriptionOverrides.getUserAgent(context, old.uuid),
                    ).forEach { (k, v) ->
                        header(k, v)
                    }
                }
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.headers["subscription-userinfo"] == null) return

                val usage = SubscriptionUsage.parse(response.headers["subscription-userinfo"])
                val upload = usage?.upload ?: 0L
                val download = usage?.download ?: 0L
                val total = usage?.total ?: 0L
                val expire = usage?.expireAt?.times(1000L) ?: 0L
                val renamed = deriveTitleFromHeaders(response.headers)
                val effectiveName = if (looksLikeGeneratedTokenName(old.name) && !renamed.isNullOrBlank()) {
                    renamed
                } else {
                    old.name
                }

                val new = Imported(
                    old.uuid,
                    effectiveName,
                    old.type,
                    old.source,
                    old.interval,
                    upload,
                    download,
                    total,
                    expire,
                    old.createdAt
                )

                ImportedDao().update(new)

                PendingDao().remove(new.uuid)
                context.sendProfileChanged(new.uuid)
            }

        } catch (e: Exception) {
            Log.w("updateFlow failed", e)
        }
    }

    private fun deriveTitleFromHeaders(headers: okhttp3.Headers): String? {
        val raw = listOf(
            "Subscription-Title",
            "Profile-Title",
            "X-Subscription-Title",
            "Display-Name",
            "X-Display-Name",
            "Subscription-Display-Name",
        ).firstNotNullOfOrNull { key ->
            headers[key]?.trim()?.trim('"', '\'')
        } ?: return null

        val decoded = MaybeBase64.decode(raw).trim()
        if (decoded.isBlank()) return null
        if (decoded.length > 64) return decoded.take(64)
        return decoded
    }

    private fun looksLikeGeneratedTokenName(name: String): Boolean {
        val n = name.trim()
        if (n.length < 12 || n.length > 48) return false
        if (n.contains(' ')) return false
        if (n.contains('.') || n.contains('/')) return false
        return n.matches(Regex("^[A-Za-z0-9_-]{12,48}$"))
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
    }

    override suspend fun queryByUUID(uuid: UUID): Profile? {
        return resolveProfile(uuid)
    }

    override suspend fun queryAll(): List<Profile> {
        val uuids = withContext(Dispatchers.IO) {
            (ImportedDao().queryAllUUIDs() + PendingDao().queryAllUUIDs()).distinct()
        }

        return uuids.mapNotNull { resolveProfile(it) }
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

    override suspend fun readProxyGroupsPreview(uuid: UUID): Map<String, List<String>> {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) {
                return@withContext emptyMap()
            }
            val file = File(context.importedDir, "$uuid/config.yaml")
            if (!file.isFile) {
                return@withContext emptyMap()
            }
            try {
                val configText = file.readText()
                ProxyGroupsYamlPreview.parseProxyNamesByGroup(configText)
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
                context.sendProfileChanged(uuid)
                true
            } catch (_: Exception) {
                false
            }
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
                context.sendProfileChanged(uuid)
                true
            } catch (_: Exception) {
                false
            }
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
                context.sendProfileChanged(uuid)
                true
            } catch (_: Exception) {
                false
            }
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
                context.sendProfileChanged(uuid)
                true
            } catch (_: Exception) {
                false
            }
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
                if (ok) context.sendProfileChanged(uuid)
                ok
            } catch (_: Exception) {
                false
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
                if (ok) context.sendProfileChanged(uuid)
                ok
            } catch (_: Exception) {
                false
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
            Log.d("applyRuleState ok=$ok")
            ok
        }
    }

    override suspend fun addRules(
        uuid: UUID,
        rawRules: List<String>,
        addMode: Boolean,
        insertMode: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) return@withContext false
            val ok = ruleApplyService.addRules(uuid, rawRules, addMode, insertMode)
            Log.d("addRules addMode=$addMode insertMode=$insertMode count=${rawRules.size} ok=$ok")
            ok
        }
    }

    override suspend fun mutateRule(uuid: UUID, ruleId: String, action: String, enabled: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            if (ImportedDao().queryByUUID(uuid) == null) return@withContext false
            val ok = ruleApplyService.mutateRule(uuid, ruleId, action, enabled)
            Log.d("mutateRule action=$action enabled=$enabled ok=$ok")
            ok
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
                ProxyYamlPreview.extractProxyEntry(file.readText(), proxyName)
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

    private suspend fun resolveProfile(uuid: UUID): Profile? {
        val imported = ImportedDao().queryByUUID(uuid)
        val pending = PendingDao().queryByUUID(uuid)

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
            pending != null
        )
    }

    private fun resolveUpdatedAt(uuid: UUID): Long {
        return context.pendingDir.resolve(uuid.toString()).directoryLastModified
            ?: context.importedDir.resolve(uuid.toString()).directoryLastModified
            ?: -1
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
