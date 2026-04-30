package com.github.kr328.clash

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.github.kr328.clash.design.LanguageSettingsDesign
import com.github.kr328.clash.util.ApplicationObserver
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class LanguageSettingsActivity : BaseActivity<LanguageSettingsDesign>() {
    override suspend fun main() {
        val design = LanguageSettingsDesign(this, uiStore)
        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                design.requests.onReceive { request ->
                    if (request == LanguageSettingsDesign.Request.ApplyLanguage) {
                        val tag = uiStore.appLanguage.tag
                        val locales = if (tag.isEmpty()) {
                            LocaleListCompat.getEmptyLocaleList()
                        } else {
                            LocaleListCompat.forLanguageTags(tag)
                        }
                        AppCompatDelegate.setApplicationLocales(locales)
                        ApplicationObserver.createdActivities.forEach { it.recreate() }
                    }
                }
            }
        }
    }
}
