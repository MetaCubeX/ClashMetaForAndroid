package com.github.kr328.clash

import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.WebDavSettingsDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.sync.VergeBackup
import com.github.kr328.clash.sync.WebDavClient
import com.github.kr328.clash.sync.WebDavException
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

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

        design.showToast(R.string.webdav_syncing, ToastDuration.Long)

        // Download the newest backup off the WebDAV server and parse out its subscriptions.
        val parsed = try {
            withContext(Dispatchers.IO) {
                val client = WebDavClient(url, user, pass)
                val backups = client.listBackups()
                if (backups.isEmpty()) null else VergeBackup.parse(client.download(backups.first()))
            }
        } catch (e: WebDavException) {
            design.showToast(getString(R.string.webdav_sync_failed, e.message ?: ""), ToastDuration.Long)
            return
        } catch (e: Exception) {
            design.showToast(
                getString(R.string.webdav_sync_failed, e.message ?: e.javaClass.simpleName),
                ToastDuration.Long,
            )
            return
        }

        if (parsed == null) {
            design.showToast(R.string.webdav_no_backup_found, ToastDuration.Long)
            return
        }

        // Import each subscription, skipping ones already present (matched by source URL),
        // so repeated syncs are idempotent rather than creating duplicates.
        var added = 0
        var existed = 0
        var failed = 0

        withProfile {
            val knownSources = queryAll()
                .mapNotNull { it.source.takeIf(String::isNotEmpty) }
                .toHashSet()

            for (remote in parsed.remotes) {
                if (remote.url in knownSources) {
                    existed++
                    continue
                }

                val uuid = create(Profile.Type.Url, remote.name, remote.url)
                try {
                    commit(uuid)
                    knownSources.add(remote.url)
                    added++
                } catch (e: Exception) {
                    // Fetch/validation failed — drop the half-created pending profile.
                    try {
                        release(uuid)
                    } catch (_: Exception) {
                    }
                    failed++
                }
            }
        }

        design.showToast(
            getString(R.string.webdav_sync_result, added, existed, failed, parsed.skippedLocal),
            ToastDuration.Long,
        )
    }
}
