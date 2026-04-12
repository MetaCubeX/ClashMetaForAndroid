package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.common.store.CoreStore
import com.github.kr328.clash.common.store.CoreStore.PendingAction
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.design.PropertiesDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import com.github.kr328.clash.design.R

class PropertiesActivity : BaseActivity<PropertiesDesign>() {
    private var canceled: Boolean = false
    private var committing: Boolean = false
    private var awaitingCoreReload: Boolean = false
    private lateinit var original: Profile

    override suspend fun main() {
        setResult(RESULT_CANCELED)

        val uuid = intent.uuid ?: return finish()
        val design = PropertiesDesign(this)

        original = withProfile { queryByUUID(uuid) } ?: return finish()

        design.profile = original

        setContentDesign(design)

        defer {
            canceled = true

            withProfile { release(uuid) }
        }

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStop -> {
                            val profile = design.profile

                            if (!canceled && !committing && profile != original) {
                                withProfile {
                                    patch(profile.uuid, profile.name, profile.source, profile.interval, profile.coreMode)
                                }
                            }
                        }
                        Event.ServiceRecreated -> {
                            if (!awaitingCoreReload) {
                                finish()
                            }
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        PropertiesDesign.Request.BrowseFiles -> {
                            startActivity(FilesActivity::class.intent.setUUID(uuid))
                        }
                        PropertiesDesign.Request.SelectCoreMode -> {
                            requestCoreMode(design.profile.coreMode)?.let { mode ->
                                if (mode != design.profile.coreMode) {
                                    design.profile = design.profile.copy(coreMode = mode)
                                }
                            }
                        }
                        PropertiesDesign.Request.Commit -> {
                            design.verifyAndCommit()
                        }
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        design?.apply {
            launch {
                if (!progressing) {
                    if (original == profile || requestExitWithoutSaving())
                        finish()
                }
            }
        } ?: return super.onBackPressed()
    }

    private suspend fun PropertiesDesign.verifyAndCommit() {
        when {
            profile.name.isBlank() -> {
                showToast(R.string.empty_name, ToastDuration.Long)
            }
            profile.type != Profile.Type.File && profile.source.isBlank() -> {
                showToast(R.string.invalid_url, ToastDuration.Long)
            }
            else -> {
                try {
                    committing = true
                    val switchedCore = profile.coreMode != currentCoreMode()
                    withProcessing { updateStatus ->
                        Log.i("Properties commit start uuid=${profile.uuid} core=${profile.coreMode}")
                        withProfile {
                            Log.i("Properties patch begin uuid=${profile.uuid}")
                            patch(profile.uuid, profile.name, profile.source, profile.interval, profile.coreMode)
                            Log.i("Properties patch end uuid=${profile.uuid}")
                        }

                        if (switchedCore) {
                            awaitingCoreReload = true
                            try {
                                Log.i("Properties core reload begin target=${profile.coreMode} uuid=${profile.uuid}")
                                scheduleCoreRestart(profile.coreMode, PendingAction.ReloadCore)
                                Log.i("Properties core reload end target=${profile.coreMode} uuid=${profile.uuid}")
                            } finally {
                                awaitingCoreReload = false
                            }
                        }

                        withProfile {
                            coroutineScope {
                                Log.i("Properties commit invoke uuid=${profile.uuid}")
                                commit(profile.uuid) {
                                    launch {
                                        updateStatus(it)
                                    }
                                }
                                Log.i("Properties commit finished uuid=${profile.uuid}")
                            }
                        }
                    }

                    setResult(RESULT_OK)

                    finish()
                } catch (e: Exception) {
                    awaitingCoreReload = false
                    showExceptionToast(e)
                } finally {
                    committing = false
                    CoreStore(this@PropertiesActivity).clearPendingAction()
                }
            }
        }
    }
}
