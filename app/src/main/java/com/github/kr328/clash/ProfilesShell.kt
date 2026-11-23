package com.github.kr328.clash

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.ProfilesDesign
import com.github.kr328.clash.design.model.ProfileProvider
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.withProfile
import io.github.g00fy2.quickie.QRResult.QRError
import io.github.g00fy2.quickie.QRResult.QRMissingPermission
import io.github.g00fy2.quickie.QRResult.QRSuccess
import io.github.g00fy2.quickie.QRResult.QRUserCanceled
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

internal class ProfilesShell(
    private val host: BaseActivity<*>,
    private val embedded: Boolean,
    private val onMounted: suspend (ProfilesDesign) -> Unit,
) {
    var page: ProfilesDesign? = null
        private set
    private var pendingSection: Int? = null
    private val gate = Mutex()
    private val refreshSignals = Channel<Unit>(Channel.CONFLATED)

    fun poke() {
        refreshSignals.trySend(Unit)
    }

    fun finishUpdate(uuid: UUID) {
        page?.finishUpdate(uuid)
    }

    fun showSection(index: Int) {
        val current = page
        if (current == null) pendingSection = index else current.openSection(index)
    }

    suspend fun refresh() {
        val current = page ?: return
        gate.withLock { fetch(current) }
    }

    suspend fun run() {
        val design = ProfilesDesign(host, embedded)
        page = design
        try {
            onMounted(design)
            pendingSection?.let {
                design.openSection(it)
                pendingSection = null
            }
            refresh()
            val clock = with(host) { ticker(TimeUnit.MINUTES.toMillis(1)) }
            while (coroutineContext.isActive) {
                select<Unit> {
                    refreshSignals.onReceive { refresh() }
                    if (!embedded) {
                        host.shellEvents().onReceive {
                            when (it) {
                                BaseActivity.Event.ActivityStart,
                                BaseActivity.Event.ProfileChanged -> refresh()
                                else -> Unit
                            }
                        }
                    }
                    design.requests.onReceive { request ->
                        when (request) {
                            is ProfilesDesign.Request.Create ->
                                createProfile(design, request.provider)
                            ProfilesDesign.Request.RefreshProviders ->
                                design.patchProviders(queryProfileProvidersOrEmpty())
                            ProfilesDesign.Request.UpdateAll ->
                                withProfile {
                                    queryAll().forEach { profile ->
                                        if (profile.imported && profile.type != Profile.Type.File) {
                                            try {
                                                update(profile.uuid, notify = false)
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (_: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    design.finishUpdate(profile.uuid)
                                                }
                                            }
                                        }
                                    }
                                }
                            is ProfilesDesign.Request.Update ->
                                try {
                                    withProfile { update(request.profile.uuid, notify = false) }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    withContext(Dispatchers.Main) {
                                        design.finishUpdate(request.profile.uuid)
                                    }
                                }
                            is ProfilesDesign.Request.Delete ->
                                withProfile { delete(request.profile.uuid) }
                            is ProfilesDesign.Request.Edit ->
                                host.startActivity(PropertiesActivity::class.intent.setUUID(request.profile.uuid))
                            is ProfilesDesign.Request.Preview -> {
                                design.beginPreview(request.profile)
                                try {
                                    val result = withProfile { preview(request.profile.uuid) }
                                    withContext(Dispatchers.Main) {
                                        design.showPreview(result)
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        design.showPreviewError(e.message ?: "Unknown")
                                    }
                                }
                            }
                            is ProfilesDesign.Request.Active ->
                                withProfile {
                                    if (request.profile.imported) {
                                        setActive(request.profile)
                                    } else {
                                        design.requestSave(request.profile)
                                    }
                                }
                            is ProfilesDesign.Request.Duplicate -> {
                                val uuid = withProfile { clone(request.profile.uuid) }
                                host.startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                            }
                            is ProfilesDesign.Request.OpenFiles ->
                                host.startActivity(FilesActivity::class.intent.setUUID(request.profile.uuid))
                            ProfilesDesign.Request.OpenGeo ->
                                host.startActivity(
                                    MetaFeatureSettingsActivity::class.intent.putExtra(
                                        MetaFeatureSettingsActivity.EXTRA_FOCUS_GEO,
                                        true,
                                    ),
                                )
                            is ProfilesDesign.Request.Reorder -> {
                                withProfile { setProfileOrder(request.uuids) }
                                refresh()
                            }
                        }
                    }
                    if (host.shellStarted()) {
                        clock.onReceive { design.updateElapsed() }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            design.cancel()
            if (page === design) page = null
        }
    }

    private suspend fun fetch(design: ProfilesDesign) {
        withProfile { design.patchProfiles(queryAll()) }
    }

    private suspend fun createProfile(design: ProfilesDesign, provider: ProfileProvider) {
        val name = host.getString(com.github.kr328.clash.design.R.string.new_profile)
        val uuid = when (provider) {
            is ProfileProvider.File -> withProfile { create(Profile.Type.File, name) }
            is ProfileProvider.Url -> withProfile { create(Profile.Type.Url, name) }
            is ProfileProvider.QR -> createFromQr(design, name)
            is ProfileProvider.External -> createFromExternal(provider, name)
        }
        if (uuid != null) openProperties(uuid)
    }

    private suspend fun createFromQr(design: ProfilesDesign, name: String): UUID? {
        return when (val result = host.startActivityForResult(ScanQRCode(), null)) {
            is QRSuccess -> {
                val url = result.content.rawValue
                    ?: result.content.rawBytes?.let { String(it) }.orEmpty()
                withProfile { create(Profile.Type.Url, name, url) }
            }
            QRUserCanceled -> null
            QRMissingPermission -> {
                design.showExceptionToast(
                    host.getString(com.github.kr328.clash.design.R.string.import_from_qr_no_permission),
                )
                null
            }
            is QRError -> {
                design.showExceptionToast(
                    host.getString(com.github.kr328.clash.design.R.string.import_from_qr_exception),
                )
                null
            }
        }
    }

    private suspend fun createFromExternal(provider: ProfileProvider.External, name: String): UUID? {
        val result = host.startActivityForResult(
            ActivityResultContracts.StartActivityForResult(),
            provider.intent,
        )
        if (result.resultCode != Activity.RESULT_OK) return null
        val uri = result.data?.data ?: return null
        val initialName = result.data?.getStringExtra(Intents.EXTRA_NAME)
        return withProfile {
            create(Profile.Type.External, initialName ?: name, uri.toString())
        }
    }

    private suspend fun openProperties(uuid: UUID) {
        host.startActivityForResult(
            ActivityResultContracts.StartActivityForResult(),
            PropertiesActivity::class.intent.setUUID(uuid),
        )
        refresh()
    }

    private suspend fun queryProfileProviders(): List<ProfileProvider> {
        return withContext(Dispatchers.IO) {
            val packageManager = host.packageManager
            val providers = packageManager.queryIntentActivities(
                Intent(Intents.ACTION_PROVIDE_URL),
                0,
            ).map {
                val activity = it.activityInfo
                val name = activity.applicationInfo.loadLabel(packageManager)
                val summary = activity.loadLabel(packageManager)
                val icon = activity.loadIcon(packageManager)
                val intent = Intent(Intents.ACTION_PROVIDE_URL)
                    .setComponent(ComponentName(activity.packageName, activity.name))
                ProfileProvider.External(name.toString(), summary.toString(), icon, intent)
            }
            providers
        }
    }

    private suspend fun queryProfileProvidersOrEmpty(): List<ProfileProvider> {
        return try {
            queryProfileProviders()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
}
