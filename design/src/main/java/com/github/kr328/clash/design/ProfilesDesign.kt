package com.github.kr328.clash.design

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ios.ProfilesScreen
import com.github.kr328.clash.design.ios.iosConfirm
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.github.kr328.clash.design.util.showSubscriptionQr
import com.github.kr328.clash.design.util.isShareableSubscriptionUrl
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private var insets by mutableStateOf(Insets.EMPTY)
    private var profiles by mutableStateOf<List<Profile>>(emptyList())
    private var loaded by mutableStateOf(false)
    private var updatable by mutableStateOf(false)
    private var allUpdating by mutableStateOf(false)
    private var now by mutableStateOf(System.currentTimeMillis())

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val title = (context as? Activity)?.title?.toString()?.ifBlank { null }
            ?: context.getString(R.string.profiles)
        setContent {
            ClashMetaXTheme {
                ProfilesScreen(
                    insets = insets,
                    title = title,
                    profiles = profiles,
                    loaded = loaded,
                    updatable = updatable,
                    allUpdating = allUpdating,
                    now = now,
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onCreate = { requestCreate() },
                    onUpdateAll = { requestUpdateAll() },
                    onActive = { requestActive(it) },
                    onUpdate = { requestUpdate(it) },
                    onEdit = { requestEdit(it) },
                    onShare = { requestShare(it) },
                    onDuplicate = { requestDuplicate(it) },
                    onDelete = { requestDelete(it) },
                )
            }
        }
    }

    suspend fun patchProfiles(profiles: List<Profile>) {
        val canUpdate = withContext(Dispatchers.Default) {
            profiles.any { it.imported && it.type != Profile.Type.File }
        }
        withContext(Dispatchers.Main) {
            this@ProfilesDesign.profiles = profiles
            updatable = canUpdate
            loaded = true
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
        requests.trySend(Request.Update(profile))
    }

    private fun requestEdit(profile: Profile) {
        requests.trySend(Request.Edit(profile))
    }

    private fun requestShare(profile: Profile) {
        showSubscriptionQr(profile.name, profile.source)
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
