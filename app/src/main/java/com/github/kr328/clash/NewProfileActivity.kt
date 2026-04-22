package com.github.kr328.clash

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.ShareImportSupport
import com.github.kr328.clash.common.util.SubscriptionNameGuesser
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.NewProfileDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.ProfileProvider
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.withProfile
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.QRResult.QRError
import io.github.g00fy2.quickie.QRResult.QRMissingPermission
import io.github.g00fy2.quickie.QRResult.QRSuccess
import io.github.g00fy2.quickie.QRResult.QRUserCanceled
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.*

class NewProfileActivity : BaseActivity<NewProfileDesign>() {
    private val self: NewProfileActivity
        get() = this

    private val scanLauncher = registerForActivityResult(ScanQRCode(), ::scanResultHandler)

    override suspend fun main() {
        val design = NewProfileDesign(this)

        design.patchProviders(queryProfileProviders())

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
                    when (it) {
                        is NewProfileDesign.Request.Create -> {
                            withProfile {
                                val name = getString(R.string.new_profile)

                                val uuid: UUID? = when (val p = it.provider) {
                                    is ProfileProvider.File ->
                                        create(Profile.Type.File, name)

                                    is ProfileProvider.Url ->
                                        create(Profile.Type.Url, name)

                                    is ProfileProvider.QR -> {
                                        null
                                    }

                                    is ProfileProvider.External -> {
                                        val data = p.get()

                                        if (data != null) {
                                            val (uri, initialName) = data

                                            create(
                                                Profile.Type.External,
                                                initialName ?: name,
                                                uri.toString()
                                            )
                                        } else {
                                            null
                                        }
                                    }
                                }

                                if (uuid != null)
                                    launchProperties(uuid)
                            }
                        }

                        is NewProfileDesign.Request.OpenDetail -> {
                            launchAppDetailed(it.provider)
                        }

                        is NewProfileDesign.Request.LaunchScanner -> {
                            scanLauncher.launch(null)
                        }
                    }
                }
            }
        }
    }

    private fun launchAppDetailed(provider: ProfileProvider.External) {
        val data = Uri.fromParts(
            "package",
            provider.intent.component?.packageName ?: return,
            null
        )

        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(data))
    }

    private suspend fun launchProperties(uuid: UUID) {
        val r = startActivityForResult(
            ActivityResultContracts.StartActivityForResult(),
            PropertiesActivity::class.intent.setUUID(uuid)
        )

        if (r.resultCode == Activity.RESULT_OK)
            finish()
    }

    private suspend fun ProfileProvider.External.get(): Pair<Uri, String?>? {
        val result = startActivityForResult(
            ActivityResultContracts.StartActivityForResult(),
            intent
        )

        if (result.resultCode != RESULT_OK)
            return null

        val uri = result.data?.data
        val name = result.data?.getStringExtra(Intents.EXTRA_NAME)

        if (uri != null) {
            return uri to name
        }

        return null
    }

    private suspend fun queryProfileProviders(): List<ProfileProvider> {
        return withContext(Dispatchers.IO) {
            val providers = packageManager.queryIntentActivities(
                Intent(Intents.ACTION_PROVIDE_URL),
                0
            ).map {
                val activity = it.activityInfo

                val name = activity.applicationInfo.loadLabel(packageManager)
                val summary = activity.loadLabel(packageManager)
                val icon = activity.loadIcon(packageManager)
                val intent = Intent(Intents.ACTION_PROVIDE_URL)
                    .setComponent(
                        ComponentName(
                            activity.packageName,
                            activity.name
                        )
                    )

                ProfileProvider.External(name.toString(), summary.toString(), icon, intent)
            }

            listOf(
                ProfileProvider.File(self),
                ProfileProvider.Url(self),
                ProfileProvider.QR(self)
            ) + providers
        }
    }

    private fun scanResultHandler(result: QRResult) {
        lifecycleScope.launch {
            when (result) {
                is QRSuccess -> {
                    val url = result.content.rawValue
                        ?: result.content.rawBytes?.let { String(it) }.orEmpty()

                    createProfileByQrCode(url)
                }

                QRUserCanceled -> {}
                QRMissingPermission -> design?.showExceptionToast(getString(R.string.import_from_qr_no_permission))
                is QRError -> design?.showExceptionToast(getString(R.string.import_from_qr_exception))
            }
        }
    }

    private suspend fun createProfileByQrCode(url: String) {
        val trimmed = url.trim()
        if (!ShareImportSupport.isAllowedUrlProfileSource(trimmed)) {
            design?.showToast(R.string.invalid_url, ToastDuration.Long)
            return
        }
        val d = design ?: return
        d.showToast(R.string.import_resolving, ToastDuration.Short)
        val name = SubscriptionNameGuesser.guess(self, trimmed)
        val uuid = withProfile {
            create(Profile.Type.Url, name, trimmed)
        }
        try {
            withProfile { commit(uuid) }
        } catch (e: Exception) {
            showImportCommitFailureDialog(uuid, e)
            return
        }
        val profile = withProfile { queryByUUID(uuid) }
        if (profile?.imported == true) {
            withProfile { setActive(profile) }
            d.showToast(getString(R.string.import_done_named, name), ToastDuration.Long)
            finish()
        } else {
            launchProperties(uuid)
        }
    }

    /** Commit failed after QR scan: show error in a dialog first; Properties only if user chooses. */
    private suspend fun showImportCommitFailureDialog(uuid: UUID, e: Throwable) {
        withContext(Dispatchers.Main) {
            val raw = e.message?.trim().orEmpty().ifBlank { e.javaClass.simpleName }
            val msg = if (raw.length > 6000) raw.take(6000) + "…" else raw
            MaterialAlertDialogBuilder(self)
                .setTitle(getString(R.string.import_failed_title))
                .setMessage(msg)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getString(R.string.import_failed_open_editor)) { _, _ ->
                    // Do not use suspend startActivityForResult from a dialog click — it can fail to open; plain start is reliable.
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
                .show()
        }
    }

}
