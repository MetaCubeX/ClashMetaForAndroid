package com.github.kr328.clash.design

import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.databinding.Observable
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.adapter.ProfileAdapter
import com.github.kr328.clash.design.BR
import com.github.kr328.clash.design.databinding.DesignProfilesBinding
import com.github.kr328.clash.design.databinding.DialogProfilesMenuBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.model.ProfileSortMode
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.*
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfilesDesign(context: Context) : Design<ProfilesDesign.Request>(context) {
    private companion object {
        const val SORT_ITEM_ID_BASE = 10_000
    }

    sealed class Request {
        object UpdateAll : Request()
        object Create : Request()
        data class Active(val profile: Profile) : Request()
        data class Update(val profile: Profile) : Request()
        data class Edit(val profile: Profile) : Request()
        data class OpenSubscriptionSources(val profile: Profile) : Request()
        data class Duplicate(val profile: Profile) : Request()
        data class Delete(val profile: Profile) : Request()
        data class Reorder(val profiles: List<Profile>) : Request()
    }

    private val binding = DesignProfilesBinding
        .inflate(context.layoutInflater, context.root, false)
    private val uiStore = UiStore(context)
    private val adapter = ProfileAdapter(
        this::requestActive,
        this::showMenu,
        onForceUpdate = { profile ->
            if (profile.imported && profile.type != Profile.Type.File) {
                requests.trySend(Request.Update(profile))
            }
        },
        showServerChooserInCard = false,
    )

    private var allUpdating: Boolean = false
    private var manualProfiles: List<Profile> = emptyList()
    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0,
    ) {
        override fun isLongPressDragEnabled(): Boolean =
            uiStore.profileSortMode == ProfileSortMode.Manual && adapter.profiles.size > 1

        override fun onMove(
            recyclerView: RecyclerView,
            source: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            if (uiStore.profileSortMode != ProfileSortMode.Manual) return false
            return adapter.moveProfile(source.bindingAdapterPosition, target.bindingAdapterPosition)
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (uiStore.profileSortMode == ProfileSortMode.Manual && manualProfiles != adapter.profiles) {
                val byId = (manualProfiles + adapter.profiles).associateBy { it.uuid.toString() }
                val orderedIds = ProfileOrdering.normalizeOrderIds(
                    orderedIds = adapter.profiles.map { it.uuid.toString() },
                    knownIds = manualProfiles.map { it.uuid.toString() },
                )
                val orderedProfiles = orderedIds.mapNotNull { byId[it] }
                manualProfiles = orderedProfiles
                requests.trySend(Request.Reorder(orderedProfiles))
            }
        }
    })

    override val root: View
        get() = binding.root

    suspend fun patchProfiles(profiles: List<Profile>) {
        manualProfiles = profiles
        val displayProfiles = sortProfilesForDisplay(profiles)
        adapter.apply {
            patchDataSet(this::profiles, displayProfiles, detectMove = true, id = { it.uuid })
        }

        val updatable = withContext(Dispatchers.Default) {
            profiles.any { it.imported && it.type != Profile.Type.File }
        }
        val activeCount = withContext(Dispatchers.Default) {
            profiles.count { it.active }
        }

        withContext(Dispatchers.Main) {
            binding.headerUpdateButton.visibility = if (updatable) View.VISIBLE else View.GONE
            binding.headerTitle.visibility = View.GONE
            binding.headerCount.text = context.getString(R.string.profiles_header_total_fmt, profiles.size)
            binding.headerActive.text = context.getString(R.string.profiles_header_active_fmt, activeCount)
            binding.headerSubtitle.text = context.getString(
                if (profiles.isEmpty()) R.string.profiles_header_subtitle_empty else R.string.profiles_header_subtitle_ready
            )
            val empty = profiles.isEmpty()
            binding.profilesHeaderCard.visibility = if (empty) View.GONE else View.VISIBLE
            binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
            binding.recyclerList.visibility = if (empty) View.INVISIBLE else View.VISIBLE
            changeUpdateAllButtonStatus()
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
        adapter.updateElapsed()
    }

    init {
        binding.self = this
        binding.toolbar.title = context.getString(R.string.main_open_profiles)
        configureSortMenu()

        binding.recyclerList.applyLinearAdapter(context, adapter)
        itemTouchHelper.attachToRecyclerView(binding.recyclerList)
        surface.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                if (propertyId == BR.insets) {
                    applyHeaderTopInset()
                }
            }
        })
        applyHeaderTopInset()
    }

    private fun applyHeaderTopInset() {
        val lp = binding.profilesHeaderCard.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val target = context.getPixels(R.dimen.toolbar_height) + surface.insets.top
        if (lp.topMargin == target) return
        lp.topMargin = target
        binding.profilesHeaderCard.layoutParams = lp
    }

    private fun showMenu(profile: Profile, @Suppress("UNUSED_PARAMETER") anchor: View) {
        val dialog = AppBottomSheetDialog(context)

        val binding = DialogProfilesMenuBinding
            .inflate(context.layoutInflater, dialog.window?.decorView as ViewGroup?, false)

        binding.master = this
        binding.self = dialog
        binding.profile = profile

        dialog.setContentView(binding.root)
        dialog.show()
    }

    fun requestUpdateAll() {
        allUpdating = true
        changeUpdateAllButtonStatus()
        requests.trySend(Request.UpdateAll)
    }

    fun finishUpdateAll() {
        allUpdating = false
        changeUpdateAllButtonStatus()
    }

    fun requestCreate() {
        requests.trySend(Request.Create)
    }

    private fun configureSortMenu() {
        val sortMenu = binding.toolbar.menu.addSubMenu(R.string.profiles_sort)
        ProfileSortMode.values().forEach { mode ->
            sortMenu.add(0, SORT_ITEM_ID_BASE + mode.ordinal, mode.ordinal, sortModeTitle(mode))
                .setCheckable(true)
        }
        sortMenu.item.setIcon(R.drawable.ic_baseline_swap_vert)
        sortMenu.item.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        binding.toolbar.setOnMenuItemClickListener { item ->
            val mode = ProfileSortMode.values().getOrNull(item.itemId - SORT_ITEM_ID_BASE)
                ?: return@setOnMenuItemClickListener false
            uiStore.profileSortMode = mode
            updateSortMenuChecks()
            adapter.profiles = sortProfilesForDisplay(manualProfiles)
            adapter.notifyDataSetChanged()
            true
        }
        updateSortMenuChecks()
    }

    private fun updateSortMenuChecks() {
        val mode = uiStore.profileSortMode
        val menu = binding.toolbar.menu
        for (i in 0 until menu.size()) {
            val sub = menu.getItem(i).subMenu ?: continue
            for (j in 0 until sub.size()) {
                sub.getItem(j).isChecked = sub.getItem(j).itemId == SORT_ITEM_ID_BASE + mode.ordinal
            }
        }
    }

    private fun sortModeTitle(mode: ProfileSortMode): String =
        context.getString(
            when (mode) {
                ProfileSortMode.Manual -> R.string.profiles_sort_manual
                ProfileSortMode.ActiveFirst -> R.string.profiles_sort_active_first
                ProfileSortMode.Name -> R.string.profiles_sort_name
                ProfileSortMode.LastUpdated -> R.string.profiles_sort_last_updated
            }
        )

    private fun sortProfilesForDisplay(profiles: List<Profile>): List<Profile> =
        ProfileOrdering.sortForDisplay(
            profiles = profiles,
            mode = uiStore.profileSortMode,
            active = Profile::active,
            name = Profile::name,
            updatedAt = Profile::updatedAt,
        )

    private fun requestActive(profile: Profile) {
        requests.trySend(Request.Active(profile))
    }

    fun requestUpdate(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Update(profile))

        dialog.dismiss()
    }

    fun requestEdit(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Edit(profile))

        dialog.dismiss()
    }

    fun requestOpenSubscriptionSources(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.OpenSubscriptionSources(profile))

        dialog.dismiss()
    }

    fun requestDuplicate(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Duplicate(profile))

        dialog.dismiss()
    }

    fun requestDelete(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Delete(profile))

        dialog.dismiss()
    }

    private fun changeUpdateAllButtonStatus() {
        binding.headerUpdateButton.isEnabled = !allUpdating
        binding.headerUpdateButton.alpha = if (allUpdating) 0.65f else 1f
        binding.headerUpdateButton.text = context.getString(
            if (allUpdating) R.string.profiles_updating else R.string.update
        )
    }
}
