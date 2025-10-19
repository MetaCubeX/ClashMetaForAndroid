package com.github.kr328.clash

import androidx.core.net.toUri
import com.github.kr328.clash.design.AboutDesign
import kotlinx.coroutines.isActive

class AboutActivity : BaseActivity<AboutDesign>() {
    override suspend fun main() {
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
        }.getOrDefault("Unknown")

        val design = AboutDesign(
            context = this,
            versionName = versionName
        )

        setContentDesign(design)

        while (isActive) {
            when (val req = design.requests.receive()) {
                is AboutDesign.Request.OpenUrl -> {
                    val uri = req.url.toUri()
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    } else {
                    }
                }

                AboutDesign.Request.Back -> finish()
            }
        }
    }
}
