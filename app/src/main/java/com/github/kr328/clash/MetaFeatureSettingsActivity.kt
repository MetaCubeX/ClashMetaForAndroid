package com.github.kr328.clash

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.design.MetaFeatureSettingsDesign
import com.github.kr328.clash.util.GeoDatabaseImportKind
import com.github.kr328.clash.util.GeoDatabaseImportResult
import com.github.kr328.clash.util.importGeoDatabaseFromUri
import com.github.kr328.clash.util.geoDatabaseImportAcceptedExtensionsLabel
import com.github.kr328.clash.util.withClash
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import com.github.kr328.clash.design.R


class MetaFeatureSettingsActivity : BaseActivity<MetaFeatureSettingsDesign>() {
    override suspend fun main() {
        val configuration = withClash { queryOverride(Clash.OverrideSlot.Persist) }

        defer {
            withClash {
                patchOverride(Clash.OverrideSlot.Persist, configuration)
            }
        }

        val design = MetaFeatureSettingsDesign(
            this,
            configuration
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
                    when (it) {
                        MetaFeatureSettingsDesign.Request.ResetOverride -> {
                            if (design.requestResetConfirm()) {
                                defer {
                                    withClash {
                                        clearOverride(Clash.OverrideSlot.Persist)
                                    }
                                }
                                finish()
                            }
                        }
                        MetaFeatureSettingsDesign.Request.ImportGeoIp -> {
                            val uri = startActivityForResult(
                                ActivityResultContracts.GetContent(),
                                "*/*")
                            importGeoFile(uri, GeoDatabaseImportKind.GeoIp)
                        }
                        MetaFeatureSettingsDesign.Request.ImportGeoSite -> {
                            val uri = startActivityForResult(
                                ActivityResultContracts.GetContent(),
                                "*/*")
                            importGeoFile(uri, GeoDatabaseImportKind.GeoSite)
                        }
                        MetaFeatureSettingsDesign.Request.ImportCountry -> {
                            val uri = startActivityForResult(
                                ActivityResultContracts.GetContent(),
                                "*/*")
                            importGeoFile(uri, GeoDatabaseImportKind.CountryMmdb)
                        }
                        MetaFeatureSettingsDesign.Request.ImportASN -> {
                            val uri = startActivityForResult(
                                ActivityResultContracts.GetContent(),
                                "*/*")
                            importGeoFile(uri, GeoDatabaseImportKind.AsnMmdb)
                        }
                    }
                }
            }
        }
    }

    private suspend fun importGeoFile(uri: Uri?, kind: GeoDatabaseImportKind) {
        when (importGeoDatabaseFromUri(uri, kind)) {
            GeoDatabaseImportResult.BadExtension ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.geofile_unknown_db_format)
                    .setMessage(
                        getString(
                            R.string.geofile_unknown_db_format_message,
                            geoDatabaseImportAcceptedExtensionsLabel(),
                        ),
                    )
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            else -> Unit
        }
    }
}