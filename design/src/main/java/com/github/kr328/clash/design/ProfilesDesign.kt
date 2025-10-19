package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.kr328.clash.design.ui.ObservableCurrentTime
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class ProfilesDesign(context: Context) : Design<ProfilesDesign.Request>(context) {
    sealed class Request {
        object UpdateAll : Request()
        object Create : Request()
        data class Active(val profile: Profile) : Request()
        data class Update(val profile: Profile) : Request()
        data class Edit(val profile: Profile) : Request()
        data class Duplicate(val profile: Profile) : Request()
        data class Delete(val profile: Profile) : Request()
    }

    internal var profiles by mutableStateOf<List<Profile>>(emptyList())
    internal var allUpdating by mutableStateOf(false)
    internal var selectedUUID by mutableStateOf<UUID?>(null)

    internal var modeIndex by mutableStateOf(0)

    internal var showDialog by mutableStateOf(false)
    internal var dialogProfile: Profile? by mutableStateOf(null)

    internal var showMenuDialog by mutableStateOf(false)
    internal var menuProfile: Profile? by mutableStateOf(null)

    private val currentTime = ObservableCurrentTime()

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.ProfilesScreen(this)
    }

    suspend fun patchProfiles(newProfiles: List<Profile>) {
        withContext(Dispatchers.Main.immediate) {
            profiles = newProfiles
            val updatable = newProfiles.any { it.imported && it.type != Profile.Type.File }
            if (!updatable && allUpdating) allUpdating = false
        }
    }

    suspend fun requestSave(profile: Profile) {
        withContext(Dispatchers.Main) {
            dialogProfile = profile
            showDialog = true
        }
    }

    fun updateElapsed() {
        currentTime.update()
    }

    fun requestUpdateAll() {
        if (allUpdating) return
        allUpdating = true
        requests.trySend(Request.UpdateAll)
    }

    fun finishUpdateAll() {
        allUpdating = false
    }

    fun requestCreate() {
        requests.trySend(Request.Create)
    }

    fun requestActive(profile: Profile) {
        requests.trySend(Request.Active(profile))
    }

    fun clearSelection() {
        selectedUUID = null
    }

    fun requestUpdate(dialog: android.app.Dialog, profile: Profile) {
        requests.trySend(Request.Update(profile))
        dialog.dismiss()
    }

    fun requestEdit(dialog: android.app.Dialog, profile: Profile) {
        requests.trySend(Request.Edit(profile))
        dialog.dismiss()
    }

    fun requestDuplicate(dialog: android.app.Dialog, profile: Profile) {
        requests.trySend(Request.Duplicate(profile))
        dialog.dismiss()
    }

    fun requestDelete(dialog: android.app.Dialog, profile: Profile) {
        requests.trySend(Request.Delete(profile))
        dialog.dismiss()
    }
}
