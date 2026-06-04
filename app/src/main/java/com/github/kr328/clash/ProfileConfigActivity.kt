package com.github.kr328.clash

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.design.ProfileConfigDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class ProfileConfigActivity : BaseActivity<ProfileConfigDesign>() {
    override suspend fun main() {
        val uuid = intent.uuid ?: return finish()
        val design = ProfileConfigDesign(this)
        setContentDesign(design)

        val profile = withProfile { queryByUUID(uuid) }
        design.setTitle(
            profile?.name?.takeIf { it.isNotBlank() }
                ?: getString(R.string.profile_menu_view_config),
        )

        // Read-only snapshot of the on-disk config; never written back.
        val config = withProfile { readConfigYaml(uuid) }.orEmpty()
        design.setConfig(config)

        while (isActive) {
            select<Unit> {
                design.requests.onReceive { req ->
                    when (req) {
                        ProfileConfigDesign.Request.Copy -> {
                            if (config.isNotBlank()) {
                                val clip = ClipData.newPlainText("config.yaml", config)
                                getSystemService<ClipboardManager>()?.setPrimaryClip(clip)
                                design.showToast(R.string.copied, ToastDuration.Short)
                            }
                        }
                        ProfileConfigDesign.Request.Download -> {
                            if (config.isNotBlank()) {
                                val baseName = profile?.name?.takeIf { it.isNotBlank() } ?: "config"
                                val fileName = baseName.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".yaml"
                                val output = startActivityForResult(
                                    ActivityResultContracts.CreateDocument("text/yaml"),
                                    fileName,
                                )
                                if (output != null) {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            contentResolver.openOutputStream(output)?.use {
                                                it.write(config.toByteArray())
                                            }
                                        }
                                        design.showToast(R.string.file_exported, ToastDuration.Long)
                                    } catch (e: Exception) {
                                        design.showExceptionToast(e)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
