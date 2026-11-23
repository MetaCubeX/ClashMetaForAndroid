package com.github.kr328.clash.design

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import universe.agentic.clash.AgenticClashTheme
import universe.agentic.clash.GeoFileUi
import universe.agentic.clash.ProfilesScreen
import universe.agentic.clash.iosConfirm
import com.github.kr328.clash.design.model.ProfileProvider
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.github.kr328.clash.design.util.showSubscriptionQr
import com.github.kr328.clash.design.util.isShareableSubscriptionUrl
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.core.model.ProfilePreview
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ProfilesDesign(
    context: Context,
    private val embedded: Boolean = false,
) : Design<ProfilesDesign.Request>(context) {
    sealed class Request {
        object UpdateAll : Request()
        object RefreshProviders : Request()
        data class Create(val provider: ProfileProvider) : Request()
        data class Active(val profile: Profile) : Request()
        data class Update(val profile: Profile) : Request()
        data class Edit(val profile: Profile) : Request()
        data class Preview(val profile: Profile) : Request()
        data class Duplicate(val profile: Profile) : Request()
        data class Delete(val profile: Profile) : Request()
        data class OpenFiles(val profile: Profile) : Request()
        object OpenGeo : Request()
        data class Reorder(val uuids: List<UUID>) : Request()
    }

    private var insets by mutableStateOf(Insets.EMPTY)
    private var profiles by mutableStateOf<List<Profile>>(emptyList())
    private var loaded by mutableStateOf(false)
    private var updatable by mutableStateOf(false)
    private var allUpdating by mutableStateOf(false)
    private var updating by mutableStateOf<Set<UUID>>(emptySet())
    private var now by mutableStateOf(System.currentTimeMillis())
    private var geoFiles by mutableStateOf<List<GeoFileUi>>(emptyList())
    private var profileFileDetails by mutableStateOf<Map<String, String>>(emptyMap())
    private var providers by mutableStateOf<List<ProfileProvider.External>>(emptyList())
    private var previewProfile by mutableStateOf<Profile?>(null)
    private var previewLoading by mutableStateOf(false)
    private var previewResult by mutableStateOf<ProfilePreview?>(null)
    private var previewError by mutableStateOf<String?>(null)
    var sectionJump by mutableIntStateOf(0)
        private set
    var sectionIndex by mutableIntStateOf(0)
        private set

    fun openSection(index: Int) {
        sectionIndex = index
        sectionJump += 1
    }

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(
            if (embedded) ViewCompositionStrategy.DisposeOnDetachedFromWindow
            else ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        val title = if (embedded) {
            context.getString(R.string.page_profiles)
        } else {
            (context as? Activity)?.title?.toString()?.ifBlank { null }
                ?: context.getString(R.string.profiles)
        }
        setContent {
            AgenticClashTheme {
                ProfilesScreen(
                    insets = insets,
                    title = title,
                    profiles = profiles,
                    loaded = loaded,
                    updatable = updatable,
                    allUpdating = allUpdating,
                    updating = updating,
                    now = now,
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    providers = providers,
                    onRefreshProviders = { requests.trySend(Request.RefreshProviders) },
                    onCreateProvider = { requestCreate(it) },
                    onUpdateAll = { requestUpdateAll() },
                    onActive = { requestActive(it) },
                    onUpdate = { requestUpdate(it) },
                    onEdit = { requestEdit(it) },
                    onShare = { requestShare(it) },
                    onPreview = { requestPreview(it) },
                    onDuplicate = { requestDuplicate(it) },
                    onDelete = { requestDelete(it) },
                    geoFiles = geoFiles,
                    profileFileDetails = profileFileDetails,
                    onOpenFiles = { requests.trySend(Request.OpenFiles(it)) },
                    onOpenGeo = { requests.trySend(Request.OpenGeo) },
                    onReorder = { uuids ->
                        val byId = profiles.associateBy { it.uuid }
                        val next = uuids.mapNotNull { byId[it] }
                        if (next.size == profiles.size) profiles = next
                        requests.trySend(Request.Reorder(uuids))
                    },
                    previewProfile = previewProfile,
                    previewLoading = previewLoading,
                    previewResult = previewResult,
                    previewError = previewError,
                    onDismissPreview = { dismissPreview() },
                    embedded = embedded,
                    sectionJump = sectionJump,
                    sectionIndex = sectionIndex,
                )
            }
        }
    }

    suspend fun patchProfiles(profiles: List<Profile>) {
        val canUpdate = withContext(Dispatchers.Default) {
            profiles.any { it.imported && it.type != Profile.Type.File }
        }
        val geo = withContext(Dispatchers.IO) { geoFileRows() }
        val details = withContext(Dispatchers.IO) { profileFileSummaries(profiles) }
        withContext(Dispatchers.Main) {
            this@ProfilesDesign.profiles = profiles
            updatable = canUpdate
            loaded = true
            geoFiles = geo
            profileFileDetails = details
        }
    }

    suspend fun patchProviders(providers: List<ProfileProvider>) {
        val external = providers.filterIsInstance<ProfileProvider.External>()
        withContext(Dispatchers.Main) {
            this@ProfilesDesign.providers = external
        }
    }

    fun beginPreview(profile: Profile) {
        previewProfile = profile
        previewLoading = true
        previewResult = null
        previewError = null
    }

    fun showPreview(result: ProfilePreview?) {
        previewLoading = false
        previewResult = result
        previewError = null
    }

    fun showPreviewError(message: String) {
        previewLoading = false
        previewResult = null
        previewError = message
    }

    private fun dismissPreview() {
        previewProfile = null
        previewLoading = false
        previewResult = null
        previewError = null
    }

    private fun profileFileSummaries(profiles: List<Profile>): Map<String, String> {
        val root = File(context.filesDir, "imported")
        return profiles.filter { it.imported }.associate { profile ->
            val dir = File(root, profile.uuid.toString())
            val children = dir.listFiles()?.toList().orEmpty()
            val detail = if (!dir.isDirectory || children.isEmpty()) {
                context.getString(R.string.list_empty)
            } else {
                val bytes = children.filter { it.isFile }.sumOf { it.length() }
                context.resources.getQuantityString(
                    R.plurals.profile_files_summary,
                    children.size,
                    children.size,
                    bytes.toBytesString(),
                )
            }
            profile.uuid.toString() to detail
        }
    }

    private fun geoFileRows(): List<GeoFileUi> {
        val dir = File(context.filesDir, "clash")
        val now = System.currentTimeMillis()
        return listOf("geoip.metadb", "geosite.dat", "ASN.mmdb").mapNotNull { name ->
            val file = File(dir, name)
            if (!file.isFile) return@mapNotNull null
            val size = file.length().toBytesString()
            val detail = if (file.lastModified() <= 0L) {
                size
            } else {
                val ago = (now - file.lastModified()).coerceAtLeast(0L).elapsedIntervalString(context)
                "$size · $ago"
            }
            GeoFileUi(name, detail)
        }
    }

    suspend fun requestSave(profile: Profile) {
        showToast(R.string.active_unsaved_tips, ToastDuration.Long) {
            setAction(R.string.edit) {
                requests.trySend(Request.Edit(profile))
            }
        }
    }

    fun updateElapsed() {
        now = System.currentTimeMillis()
    }

    fun requestUpdateAll() {
        if (allUpdating || updating.isNotEmpty()) return
        val ids = profiles
            .filter { it.imported && it.type != Profile.Type.File }
            .map { it.uuid }
            .toSet()
        if (ids.isEmpty()) return
        updating = ids
        allUpdating = true
        requests.trySend(Request.UpdateAll)
    }

    fun finishUpdate(uuid: UUID) {
        updating = updating - uuid
        if (updating.isEmpty()) allUpdating = false
    }

    fun requestCreate(provider: ProfileProvider) {
        requests.trySend(Request.Create(provider))
    }

    fun canShareSubscriptionQr(profile: Profile): Boolean {
        return isShareableSubscriptionUrl(profile.source)
    }

    fun requestUpdate(dialog: Dialog, profile: Profile) {
        dialog.dismiss()
        requestUpdate(profile)
    }

    fun requestEdit(dialog: Dialog, profile: Profile) {
        dialog.dismiss()
        requestEdit(profile)
    }

    fun requestShareQr(dialog: Dialog, profile: Profile) {
        dialog.dismiss()
        requestShare(profile)
    }

    fun requestDuplicate(dialog: Dialog, profile: Profile) {
        dialog.dismiss()
        requestDuplicate(profile)
    }

    fun requestDelete(dialog: Dialog, profile: Profile) {
        dialog.dismiss()
        requestDelete(profile)
    }

    private fun requestActive(profile: Profile) {
        requests.trySend(Request.Active(profile))
    }

    private fun requestUpdate(profile: Profile) {
        if (profile.uuid in updating) return
        updating = updating + profile.uuid
        requests.trySend(Request.Update(profile))
    }

    private fun requestEdit(profile: Profile) {
        requests.trySend(Request.Edit(profile))
    }

    private fun requestShare(profile: Profile) {
        showSubscriptionQr(profile.name, profile.source)
    }

    private fun requestPreview(profile: Profile) {
        requests.trySend(Request.Preview(profile))
    }

    private fun requestDuplicate(profile: Profile) {
        requests.trySend(Request.Duplicate(profile))
    }

    private fun requestDelete(profile: Profile) {
        confirm(context.getString(R.string.delete), profile.name, destructive = true) {
            requests.trySend(Request.Delete(profile))
        }
    }

    private fun confirm(title: String, message: String, destructive: Boolean, block: () -> Unit) {
        launch {
            val accepted = withContext(Dispatchers.Main) {
                iosConfirm(
                    context,
                    title,
                    message,
                    context.getString(R.string.ok),
                    destructive,
                )
            }
            if (accepted) block()
        }
    }

    init {
        if (context is AppCompatActivity) {
            context.window.decorView.setOnInsertsChangedListener {
                if (surface.insets != it) surface.insets = it
                insets = it
            }
        }
    }
}
