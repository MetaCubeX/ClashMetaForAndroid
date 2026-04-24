package com.github.kr328.clash

import com.github.kr328.clash.design.AnnouncementSettingsDesign
import com.github.kr328.clash.design.store.UiStore
import kotlinx.coroutines.isActive

class AnnouncementSettingsActivity : BaseActivity<AnnouncementSettingsDesign>() {
    override suspend fun main() {
        val design = AnnouncementSettingsDesign(this, UiStore(this))

        setContentDesign(design)

        while (isActive) {
            events.receive()
        }
    }
}
