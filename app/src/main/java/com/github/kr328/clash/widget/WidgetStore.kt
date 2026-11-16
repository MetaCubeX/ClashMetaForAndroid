package com.github.kr328.clash.widget

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider

enum class WidgetAppearance {
    FollowSystem,
    Light,
    Dark,
}

enum class WidgetTransparency {
    Low,
    Medium,
    High,
}

class WidgetStore(context: Context) {
    private val store = Store(
        context
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .asStoreProvider()
    )

    var appearance: WidgetAppearance by store.enum(
        key = "appearance",
        defaultValue = WidgetAppearance.FollowSystem,
        values = WidgetAppearance.values(),
    )

    var transparency: WidgetTransparency by store.enum(
        key = "transparency",
        defaultValue = WidgetTransparency.Medium,
        values = WidgetTransparency.values(),
    )

    /**
     * The node name is remembered so the widget can still show the last used node
     * after the proxy has been stopped.
     */
    var lastNode: String by store.string(
        key = "last_node",
        defaultValue = "",
    )

    companion object {
        private const val FILE_NAME = "widget"
    }
}
