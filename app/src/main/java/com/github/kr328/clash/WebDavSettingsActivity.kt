package com.github.kr328.clash

import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.WebDavSettingsDesign
import com.github.kr328.clash.design.dialog.LogDialogRetry
import com.github.kr328.clash.design.dialog.ModelLogDialogScope
import com.github.kr328.clash.design.dialog.withModelLogDialog
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IProfileManager
import com.github.kr328.clash.sync.ParsedBackup
import com.github.kr328.clash.sync.RemoteProfile
import com.github.kr328.clash.sync.VergeBackup
import com.github.kr328.clash.sync.WebDavClient
import com.github.kr328.clash.sync.WebDavException
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
                        WebDavSettingsDesign.Request.TestConnection -> testConnection(design)
                        WebDavSettingsDesign.Request.SyncNow -> performSync(design)
                    }
                }
            }
        }
    }

    private suspend fun clientOrNull(design: WebDavSettingsDesign): WebDavClient? {
        val url = uiStore.webdavUrl.trim()
        val user = uiStore.webdavUsername
        val pass = uiStore.webdavPassword

        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            design.showToast(R.string.webdav_missing_credentials, ToastDuration.Long)
            return null
        }

        return WebDavClient(url, user, pass)
    }

    private suspend fun testConnection(design: WebDavSettingsDesign) {
        val client = clientOrNull(design) ?: return

        design.showToast(R.string.webdav_testing_connection, ToastDuration.Short)

        val message = try {
            val backups = withContext(Dispatchers.IO) { client.listBackups() }

            if (backups.isEmpty()) {
                getString(R.string.webdav_test_no_backup)
            } else {
                getString(R.string.webdav_test_success, backups.size, backups.first())
            }
        } catch (e: Exception) {
            getString(R.string.webdav_test_failed, describe(e))
        }

        design.showToast(message, ToastDuration.Long)
    }

    private suspend fun performSync(design: WebDavSettingsDesign) {
        val client = clientOrNull(design) ?: return

        withModelLogDialog(getString(R.string.webdav_sync)) {
            try {
                log(getString(R.string.webdav_log_connecting, uiStore.webdavUrl.trim()))

                // Download the newest backup off the WebDAV server and parse out its subscriptions.
                val backup = withContext(Dispatchers.IO) {
                    val latest = client.listBackups().firstOrNull() ?: return@withContext null
                    latest to VergeBackup.parse(client.download(latest))
                }

                if (backup == null) {
                    log(getString(R.string.webdav_no_backup_found))
                    return@withModelLogDialog null
                }

                log(getString(R.string.webdav_log_found_backup, backup.first))
                log(getString(R.string.webdav_log_parsed, backup.second.remotes.size, backup.second.skippedLocal))

                // Replacing everything with an empty list would wipe the phone; treat it as a bad backup.
                if (backup.second.remotes.isEmpty()) {
                    log(getString(R.string.webdav_log_empty_backup))
                    return@withModelLogDialog null
                }

                if (isCancelRequested) {
                    log(getString(R.string.webdav_log_cancelled))
                    return@withModelLogDialog null
                }

                applyBackup(backup.second)
            } catch (e: Exception) {
                log(getString(R.string.webdav_sync_failed, describe(e)))
                null
            }
        }
    }

    /**
     * Mirrors Clash Verge's restore, which replaces the whole profile list with the backup's:
     * afterwards the local list holds exactly the backup's subscriptions, and the one Verge had
     * selected becomes active. Subscriptions that already exist locally (matched by Verge uid,
     * then URL, then name) are updated in place rather than recreated, so their uuid and proxy
     * selections survive; every other local profile, of any type, is deleted. If the desktop's
 * selection could not be synced, another synced subscription is made active instead, and
 * failing that the active profile is kept, so a sync never leaves the phone with none.
     *
     * Subscriptions are applied one at a time and the deletion only runs once all of them have
     * been processed, so a cancelled sync stops after the current subscription and leaves every
     * other local profile untouched.
     */
    private suspend fun ModelLogDialogScope.applyBackup(parsed: ParsedBackup): LogDialogRetry? {
        val previouslySynced = decodeSynced(uiStore.webdavSyncedProfiles)
        val synced = LinkedHashMap<String, UUID>()

        var removed = 0

        val result = withProfile {
            val existing = queryAll()
            val claimed = HashSet<UUID>()
            val applied = applyRemotes(this, parsed.remotes, existing, previouslySynced, synced, claimed)

            if (applied.cancelled) return@withProfile applied

            // Settle what ends up active before deleting anything, so the phone is never left
            // without an active profile: the desktop's selection if it synced, else, when the
            // active profile is about to go, the first subscription that did.
            val active = queryActive()
            val desktop = importedOrNull(this, parsed.current?.let { synced[it] })
            val fallback = if (desktop == null && (active == null || active.uuid !in claimed)) {
                synced.values.firstNotNullOfOrNull { importedOrNull(this, it) }
            } else {
                null
            }

            for (profile in existing) {
                if (profile.uuid in claimed) continue

                // Nothing to switch to: keep the active profile rather than leave none.
                if (desktop == null && fallback == null && profile.uuid == active?.uuid) {
                    log(getString(R.string.webdav_log_kept_active, profile.name))
                    continue
                }

                try {
                    delete(profile.uuid)
                    removed++
                    log(getString(R.string.webdav_log_removed, profile.name))
                } catch (e: Exception) {
                    log(getString(R.string.webdav_log_item_failed, profile.name, describe(e)))
                }
            }

            if (desktop != null) {
                setActive(desktop)
                log(getString(R.string.webdav_log_activated, desktop.name))
            } else if (fallback != null) {
                setActive(fallback)
                log(getString(R.string.webdav_log_activated_fallback, fallback.name))
            }

            applied
        }

        if (result.cancelled) {
            // Subscriptions not reached yet keep their old mapping for the next sync.
            uiStore.webdavSyncedProfiles = encodeSynced(previouslySynced + synced)

            log(getString(R.string.webdav_log_cancelled))
            log(getString(R.string.webdav_log_done, result.updated, result.added, 0, result.failed.size))

            return null
        }

        uiStore.webdavSyncedProfiles = encodeSynced(synced)

        log(getString(R.string.webdav_log_done, result.updated, result.added, removed, result.failed.size))

        return retryOf(parsed, result.failed, synced)
    }

    /** Re-applies only [failed]; the rest of the profile list was already settled by the sync. */
    private suspend fun ModelLogDialogScope.retryFailed(
        parsed: ParsedBackup,
        failed: List<RemoteProfile>,
        synced: LinkedHashMap<String, UUID>,
    ): LogDialogRetry? {
        log(getString(R.string.webdav_log_retrying, failed.size))

        val retried = failed.map { it.uid }.toSet()

        val result = withProfile {
            // Profiles belonging to other subscriptions must not be matched by a retried one.
            val claimed = synced.filterKeys { it !in retried }.values.toHashSet()
            val applied = applyRemotes(this, failed, queryAll(), synced, synced, claimed)

            val current = parsed.current
            if (current != null && current in applied.done && applied.failed.none { it.uid == current }) {
                activateCurrent(this, parsed, synced)
            }

            applied
        }

        uiStore.webdavSyncedProfiles = encodeSynced(synced)

        val succeeded = result.updated + result.added
        if (result.cancelled) log(getString(R.string.webdav_log_cancelled))
        log(getString(R.string.webdav_log_retry_done, succeeded, result.failed.size))

        // After a cancel, the subscriptions not reached yet are offered again too.
        val left = result.failed + failed.filter { it.uid !in result.done }

        return retryOf(parsed, left, synced)
    }

    private fun retryOf(
        parsed: ParsedBackup,
        failed: List<RemoteProfile>,
        synced: LinkedHashMap<String, UUID>,
    ): LogDialogRetry? {
        if (failed.isEmpty()) return null

        return LogDialogRetry(getString(R.string.webdav_retry_failed, failed.size)) {
            try {
                retryFailed(parsed, failed, synced)
            } catch (e: Exception) {
                log(getString(R.string.webdav_sync_failed, describe(e)))
                retryOf(parsed, failed, synced)
            }
        }
    }

    private class ApplyResult(
        val updated: Int,
        val added: Int,
        val failed: List<RemoteProfile>,
        val done: Set<String>,
        val cancelled: Boolean,
    )

    /**
     * Updates or creates a profile for each of [remotes], in order, recording the result in
     * [synced] and [claimed]. Checks for cancel between subscriptions, never in the middle of one.
     */
    private suspend fun ModelLogDialogScope.applyRemotes(
        profiles: IProfileManager,
        remotes: List<RemoteProfile>,
        existing: List<Profile>,
        known: Map<String, UUID>,
        synced: MutableMap<String, UUID>,
        claimed: MutableSet<UUID>,
    ): ApplyResult {
        var updated = 0
        var added = 0
        val failed = ArrayList<RemoteProfile>()
        val done = HashSet<String>()

        fun match(remote: RemoteProfile): Profile? {
            val free = existing.filter { it.type == Profile.Type.Url && it.uuid !in claimed }

            return free.firstOrNull { it.uuid == known[remote.uid] }
                ?: free.firstOrNull { it.source == remote.url }
                ?: free.firstOrNull { it.name == remote.name }
        }

        for ((index, remote) in remotes.withIndex()) {
            if (isCancelRequested) {
                return ApplyResult(updated, added, failed, done, cancelled = true)
            }

            progress(index, remotes.size)

            val target = match(remote)
            var uuid = target?.uuid

            // An existing profile is claimed up front, so a failed update never gets it deleted.
            if (uuid != null) claimed.add(uuid)
            done.add(remote.uid)

            try {
                val committed = if (target != null) {
                    log(getString(R.string.webdav_log_updating, remote.name))
                    profiles.patch(target.uuid, remote.name, remote.url, target.interval, target.ageSecretKey)
                    target.uuid
                } else {
                    log(getString(R.string.webdav_log_adding, remote.name))
                    val created = profiles.create(Profile.Type.Url, remote.name, remote.url)
                    uuid = created
                    claimed.add(created)
                    created
                }

                profiles.commit(committed)

                synced[remote.uid] = committed

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
                val pending = uuid
                if (pending != null) {
                    try {
                        profiles.release(pending)
                    } catch (_: Exception) {
                    }
                }

                if (target != null) synced[remote.uid] = target.uuid else synced.remove(remote.uid)

                failed.add(remote)
                log(getString(R.string.webdav_log_item_failed, remote.name, describe(e)))
            }
        }

        progress(remotes.size, remotes.size)

        return ApplyResult(updated, added, failed, done, cancelled = false)
    }

    private suspend fun ModelLogDialogScope.activateCurrent(
        profiles: IProfileManager,
        parsed: ParsedBackup,
        synced: Map<String, UUID>,
    ) {
        val current = importedOrNull(profiles, parsed.current?.let { synced[it] }) ?: return

        profiles.setActive(current)
        log(getString(R.string.webdav_log_activated, current.name))
    }

    /** The profile behind [uuid] if it has a usable config, i.e. it can be made active. */
    private suspend fun importedOrNull(profiles: IProfileManager, uuid: UUID?): Profile? {
        return uuid?.let { profiles.queryByUUID(it) }?.takeIf { it.imported }
    }

    private fun describe(e: Exception): String {
        if (e is WebDavException && e.isUnauthorized) return getString(R.string.webdav_unauthorized)

        return e.message ?: e.javaClass.simpleName
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
