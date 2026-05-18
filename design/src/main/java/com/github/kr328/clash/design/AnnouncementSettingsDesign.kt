package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.preference.NullableTextAdapter
import com.github.kr328.clash.design.preference.category
import com.github.kr328.clash.design.preference.editableText
import com.github.kr328.clash.design.preference.preferenceScreen
import com.github.kr328.clash.design.preference.switch
import com.github.kr328.clash.design.preference.tips
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

/**
 * "Announcement & Support" settings screen.
 *
 * Lets the operator (or user) configure:
 *  - support URL (e.g. Telegram bot) shown in About
 *  - announcement text + optional URL surfaced as a card on the main screen
 *
 * These values live in [UiStore] (SharedPreferences) and never touch the
 * profile DB or AIDL surface.
 */
class AnnouncementSettingsDesign(
    context: Context,
    uiStore: UiStore,
) : Design<Unit>(context) {
    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.surface = surface
        binding.toolbar.title = (context as? Activity)?.title?.toString().orEmpty()
        binding.toolbar.setNavigationOnClickListener {
            (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
        }

        // Bridge non-null UiStore strings to nullable properties expected by editableText.
        val supportUrlNullable = NullableStringProperty(uiStore::supportUrl)
        val announcementNullable = NullableStringProperty(uiStore::announcement)
        val announcementUrlNullable = NullableStringProperty(uiStore::announcementUrl)

        val screen = preferenceScreen(context) {
            tips(R.string.announcement_settings_tips)

            category(R.string.announcement_category_support)

            editableText(
                value = supportUrlNullable::value,
                adapter = NullableTextAdapter.String,
                title = R.string.support_url,
                placeholder = R.string.support_url_placeholder,
                empty = R.string.not_set,
            )

            category(R.string.announcement_category_announcement)

            editableText(
                value = announcementNullable::value,
                adapter = NullableTextAdapter.String,
                title = R.string.announcement_text,
                placeholder = R.string.announcement_text_placeholder,
                empty = R.string.not_set,
            )

            editableText(
                value = announcementUrlNullable::value,
                adapter = NullableTextAdapter.String,
                title = R.string.announcement_url,
                placeholder = R.string.announcement_url_placeholder,
                empty = R.string.not_set,
            )

            switch(
                value = uiStore::subscriptionMetadataLockUser,
                title = R.string.sub_meta_lock_user,
                summary = R.string.sub_meta_lock_user_summary,
            )

            switch(
                value = uiStore::subscriptionMetadataAllowInsecureHttp,
                title = R.string.sub_meta_allow_http,
                summary = R.string.sub_meta_allow_http_summary,
            )
        }

        binding.content.addView(screen.root)
    }

    /**
     * Treats blank stored strings as `null` so [editableText] shows the placeholder/empty hint
     * instead of an empty editable value, while persisting trimmed text back to [UiStore].
     */
    private class NullableStringProperty(
        private val backing: kotlin.reflect.KMutableProperty0<String>,
    ) {
        var value: String?
            get() = backing.get().takeIf { it.isNotBlank() }
            set(newValue) {
                backing.set(newValue?.trim().orEmpty())
            }
    }
}
