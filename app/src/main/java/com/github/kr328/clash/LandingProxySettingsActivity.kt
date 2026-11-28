package com.github.kr328.clash

import com.github.kr328.clash.design.LandingProxySettingsDesign
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.sendOverrideChanged
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class LandingProxySettingsActivity : BaseActivity<LandingProxySettingsDesign>() {
    override suspend fun main() {
        val store = ServiceStore(this)
        val initial = withContext(Dispatchers.IO) { store.landingProxyJson() }

        val design = LandingProxySettingsDesign(this, store)

        setContentDesign(design)

        defer {
            val changed = withContext(Dispatchers.IO) { store.landingProxyJson() } != initial

            // reload the running profile; a preview picks the change up on its next load
            if (changed && clashRunning) {
                sendOverrideChanged()
            }
        }

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
                    when (it) {
                        LandingProxySettingsDesign.Request.PickFrontGroup -> {
                            design.requestFrontGroup(queryGroupNames())
                        }
                    }
                }
            }
        }
    }

    private suspend fun queryGroupNames(): List<String> {
        return try {
            withClash {
                if (preparePreview() != null) {
                    return@withClash emptyList()
                }

                try {
                    queryProxyGroupNames(false).filter { it != "GLOBAL" }
                } finally {
                    releasePreview()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
