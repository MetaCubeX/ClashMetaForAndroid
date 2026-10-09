package com.github.kr328.clash

import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.WebDavSettingsDesign
import com.github.kr328.clash.design.dialog.ModelLogDialogScope
import com.github.kr328.clash.design.dialog.withModelLogDialog
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.sync.ParsedBackup
import com.github.kr328.clash.sync.VergeBackup
import com.github.kr328.clash.sync.WebDavClient
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.UUID

class WebDavSettingsActivity : BaseActivity<WebDavSettingsDesign>() {
    override suspend fun main() {
        val design = WebDavSettingsDesign(this, uiStore)

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive { }
                design.requests.onReceive {
                    when (it) {
                        WebDavSettingsDesign.Request.SyncNow -> performSync(design)
                    }
                }
            }
        }
    }

    private suspend fun performSync(design: WebDavSettingsDesign) {
        val url = uiStore.webdavUrl.trim()
        val user = uiStore.webdavUsername
        val pass = uiStore.webdavPassword

        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            design.showToast(R.string.webdav_missing_credentials, ToastDuration.Long)
            return
        }

        withModelLogDialog(getString(R.string.webdav_sync)) {
            try {
                log(getString(R.string.webdav_log_connecting, url))

                // Download the newest backup off the WebDAV server and parse out its subscriptions.
                val backup = withContext(Dispatchers.IO) {
                    val client = WebDavClient(url, user, pass)
                    val latest = client.listBackups().firstOrNull() ?: return@withContext null
                    latest to VergeBackup.parse(client.download(latest))
                }

                if (backup == null) {
                    log(getString(R.string.webdav_no_backup_found))
                    return@withModelLogDialog
                }

                log(getString(R.string.webdav_log_found_backup, backup.first))
                log(getString(R.string.webdav_log_parsed, backup.second.remotes.size, backup.second.skippedLocal))

                // Replacing everything with an empty list would wipe the phone; treat it as a bad backup.
                if (backup.second.remotes.isEmpty()) {
                    log(getString(R.string.webdav_log_empty_backup))
                    return@withModelLogDialog
                }

                applyBackup(backup.second)
            } catch (e: Exception) {
                log(getString(R.string.webdav_sync_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /**
     * Mirrors Clash Verge's restore, which replaces the whole profile list with the backup's:
     * afterwards the local list holds exactly the backup's subscriptions, and the one Verge had
     * selected becomes active. Subscriptions that already exist locally (matched by Verge uid,
     * then URL, then name) are updated in place rather than recreated, so their uuid and proxy
     * selections survive; every other local profile, of any type, is deleted.
     */
    private suspend fun ModelLogDialogScope.applyBackup(parsed: ParsedBackup) {
        val previouslySynced = decodeSynced(uiStore.webdavSyncedProfiles)
        val synced = LinkedHashMap<String, UUID>()

        var updated = 0
        var added = 0
        var removed = 0
        var failed = 0

        withProfile {
            val existing = queryAll()
            val claimed = HashSet<UUID>()

            fun match(uid: String, name: String, url: String): Profile? {
                val free = existing.filter { it.type == Profile.Type.Url && it.uuid !in claimed }

                return free.firstOrNull { it.uuid == previouslySynced[uid] }
                    ?: free.firstOrNull { it.source == url }
                    ?: free.firstOrNull { it.name == name }
            }

            parsed.remotes.forEachIndexed { index, remote ->
                progress(index, parsed.remotes.size)

                val target = match(remote.uid, remote.name, remote.url)
                val uuid = if (target != null) {
                    log(getString(R.string.webdav_log_updating, remote.name))
                    patch(target.uuid, remote.name, remote.url, target.interval, target.ageSecretKey)
                    target.uuid
                } else {
                    log(getString(R.string.webdav_log_adding, remote.name))
                    create(Profile.Type.Url, remote.name, remote.url)
                }

                claimed.add(uuid)

                try {
                    commit(uuid)

                    synced[remote.uid] = uuid

                    if (target != null) {
                        updated++
                        log(getString(R.string.webdav_log_updated, remote.name))
                    } else {
                        added++
                        log(getString(R.string.webdav_log_added, remote.name))
                    }
                } catch (e: Exception) {
                    // Fetch/validation failed — drop the pending change; an existing profile
                    // keeps its last good config and stays synced.
                    try {
                        release(uuid)
                    } catch (_: Exception) {
                    }

                    if (target != null) synced[remote.uid] = uuid

                    failed++
                    log(getString(R.string.webdav_log_item_failed, remote.name, e.message ?: e.javaClass.simpleName))
                }
            }

            progress(parsed.remotes.size, parsed.remotes.size)

            for (profile in existing) {
                if (profile.uuid in claimed) continue

                try {
                    delete(profile.uuid)
                    removed++
                    log(getString(R.string.webdav_log_removed, profile.name))
                } catch (e: Exception) {
                    failed++
                    log(getString(R.string.webdav_log_item_failed, profile.name, e.message ?: e.javaClass.simpleName))
                }
            }

            val current = parsed.current?.let { synced[it] }?.let { queryByUUID(it) }
            if (current != null && current.imported) {
                setActive(current)
                log(getString(R.string.webdav_log_activated, current.name))
            }
        }

        uiStore.webdavSyncedProfiles = encodeSynced(synced)

        log(getString(R.string.webdav_log_done, updated, added, removed, failed))
    }

    private fun decodeSynced(text: String): Map<String, UUID> {
        return text.lineSequence().mapNotNull { line ->
            val uid = line.substringBefore('\t')
            val uuid = runCatching { UUID.fromString(line.substringAfter('\t')) }.getOrNull()
            if (uid.isEmpty() || uuid == null) null else uid to uuid
        }.toMap()
    }

    private fun encodeSynced(synced: Map<String, UUID>): String {
        return synced.entries.joinToString("\n") { "${it.key}\t${it.value}" }
    }
}
