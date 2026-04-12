package com.github.kr328.clash.common.store

import android.content.Context
import com.github.kr328.clash.common.model.CoreMode
import java.util.UUID

class CoreStore(context: Context) {
    private val store = Store(
        context
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .asStoreProvider()
    )

    var currentMode: CoreMode by store.enum(
        key = "current_mode",
        defaultValue = CoreMode.Meta,
        values = CoreMode.values()
    )

    var pendingAction: PendingAction by store.enum(
        key = "pending_action",
        defaultValue = PendingAction.None,
        values = PendingAction.values()
    )

    var pendingProfileUuid: UUID? by store.typedString(
        key = "pending_profile_uuid",
        from = { raw -> raw.takeIf { it.isNotBlank() }?.let(UUID::fromString) },
        to = { it?.toString().orEmpty() }
    )

    fun clearPendingAction() {
        pendingAction = PendingAction.None
        pendingProfileUuid = null
    }

    enum class PendingAction {
        None,
        ReloadCore,
        OpenProperties,
        ActivateProfile,
        UpdateProfile,
    }

    companion object {
        private const val PREFERENCE_NAME = "core"
    }
}
