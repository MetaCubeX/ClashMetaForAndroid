package com.github.kr328.clash

import com.github.kr328.clash.common.store.CoreStore.PendingAction
import com.github.kr328.clash.common.store.CoreStore
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.ProfilesDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R

class ProfilesActivity : BaseActivity<ProfilesDesign>() {
    override suspend fun main() {
        val design = ProfilesDesign(this)

        setContentDesign(design)

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart, Event.ProfileChanged -> {
                            design.fetch()
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        ProfilesDesign.Request.Create ->
                            startActivity(NewProfileActivity::class.intent)
                        ProfilesDesign.Request.UpdateAll ->
                            try {
                                withProfile {
                                    try {
                                        queryAll().forEach { p ->
                                            if (p.imported && p.type != Profile.Type.File)
                                                update(p.uuid)
                                        }
                                    }
                                    finally {
                                        withContext(Dispatchers.Main) {
                                            design.finishUpdateAll();
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                design.showExceptionToast(e)
                            }
                        is ProfilesDesign.Request.Update ->
                            try {
                                val switchedCore = it.profile.coreMode != currentCoreMode()
                                if (switchedCore) {
                                    scheduleCoreRestart(it.profile.coreMode, PendingAction.ReloadCore, it.profile.uuid)
                                    withProfile { update(it.profile.uuid) }
                                    CoreStore(this@ProfilesActivity).clearPendingAction()
                                } else {
                                    withProfile { update(it.profile.uuid) }
                                }
                            } catch (e: Exception) {
                                CoreStore(this@ProfilesActivity).clearPendingAction()
                                design.showExceptionToast(e)
                            }
                        is ProfilesDesign.Request.Delete ->
                            try {
                                withProfile { delete(it.profile.uuid) }
                            } catch (e: Exception) {
                                design.showExceptionToast(e)
                            }
                        is ProfilesDesign.Request.Edit ->
                            startActivity(PropertiesActivity::class.intent.setUUID(it.profile.uuid))
                        is ProfilesDesign.Request.Active -> {
                            try {
                                val switchedCore = it.profile.coreMode != currentCoreMode()
                                if (switchedCore) {
                                    if (!confirmCoreRestart(it.profile.coreMode)) {
                                        return@onReceive
                                    }

                                    scheduleCoreRestart(
                                        it.profile.coreMode,
                                        PendingAction.ActivateProfile,
                                        it.profile.uuid
                                    )
                                    reloadProgramPages()
                                } else {
                                    withProfile {
                                        if (it.profile.pending || !it.profile.imported) {
                                            commit(it.profile.uuid, null)
                                        }

                                        queryByUUID(it.profile.uuid)?.let { profile ->
                                            if (profile.imported) {
                                                setActive(profile)
                                            } else {
                                                design.requestSave(profile)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                CoreStore(this@ProfilesActivity).clearPendingAction()
                                design.showExceptionToast(e)
                            }
                        }
                        is ProfilesDesign.Request.Duplicate -> {
                            val uuid = withProfile { clone(it.profile.uuid) }

                            startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                        }
                    }
                }
                if (activityStarted) {
                    ticker.onReceive {
                        design.updateElapsed()
                    }
                }
            }
        }
    }

    private suspend fun ProfilesDesign.fetch() {
        withProfile {
            patchProfiles(queryAll())
        }
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        if(uuid == null)
            return;
        launch {
            var name: String? = null;
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(R.string.toast_profile_updated_complete, name),
                ToastDuration.Long
            )
        }
    }
    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        if(uuid == null)
            return;
        launch {
            var name: String? = null;
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(R.string.toast_profile_updated_failed, name, reason),
                ToastDuration.Long
            ){
                setAction(R.string.edit) {
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
            }
        }
    }
}
