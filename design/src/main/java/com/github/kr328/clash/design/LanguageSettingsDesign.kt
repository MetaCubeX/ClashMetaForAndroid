package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.databinding.DesignLanguageSettingsBinding
import com.github.kr328.clash.design.model.AppLanguage
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class LanguageSettingsDesign(
    context: Context,
    private val uiStore: UiStore,
) : Design<LanguageSettingsDesign.Request>(context) {
    enum class Request {
        ApplyLanguage,
    }

    private val binding = DesignLanguageSettingsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.header.screenTitle.text = context.getString(R.string.app_language)
        applyCheckedLanguage()
        binding.languageGroup.setOnCheckedChangeListener { _, checkedId ->
            val lang = when (checkedId) {
                R.id.language_en -> AppLanguage.English
                R.id.language_ru -> AppLanguage.Russian
                R.id.language_zh -> AppLanguage.Chinese
                else -> AppLanguage.System
            }
            if (uiStore.appLanguage != lang) {
                uiStore.appLanguage = lang
                requests.trySend(Request.ApplyLanguage)
            }
        }
    }

    private fun applyCheckedLanguage() {
        binding.languageGroup.check(
            when (uiStore.appLanguage) {
                AppLanguage.English -> R.id.language_en
                AppLanguage.Russian -> R.id.language_ru
                AppLanguage.Chinese -> R.id.language_zh
                AppLanguage.System -> R.id.language_system
            },
        )
    }
}
