package com.github.kr328.clash

import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.design.GeoDataSourceSettingsDesign
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.GeoDatabaseImportKind
import com.github.kr328.clash.util.GeoDatabaseImportResult
import com.github.kr328.clash.util.geoDatabaseImportAcceptedExtensionsLabel
import com.github.kr328.clash.util.importGeoDatabaseFromUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import com.github.kr328.clash.design.R

class GeoDataSourceSettingsActivity : BaseActivity<GeoDataSourceSettingsDesign>() {
    override suspend fun main() {
        val service = ServiceStore(this)
        val config = GeoDataSourceSettingsDesign.Config(
            preset = service.geoDataSourcePreset,
            customGeoIp = service.geoDataCustomGeoIp.takeIf { it.isNotBlank() },
            customGeoSite = service.geoDataCustomGeoSite.takeIf { it.isNotBlank() },
            customMmdb = service.geoDataCustomMmdb.takeIf { it.isNotBlank() },
            customAsn = service.geoDataCustomAsn.takeIf { it.isNotBlank() },
        )

        defer {
            service.geoDataSourcePreset = config.preset
            service.geoDataCustomGeoIp = config.customGeoIp?.trim().orEmpty()
            service.geoDataCustomGeoSite = config.customGeoSite?.trim().orEmpty()
            service.geoDataCustomMmdb = config.customMmdb?.trim().orEmpty()
            service.geoDataCustomAsn = config.customAsn?.trim().orEmpty()
        }

        val design = GeoDataSourceSettingsDesign(this, config)
        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    // no-op
                }
                design.requests.onReceive {
                    val kind = when (it) {
                        GeoDataSourceSettingsDesign.Request.ImportGeoIp ->
                            GeoDatabaseImportKind.GeoIp
                        GeoDataSourceSettingsDesign.Request.ImportGeoSite ->
                            GeoDatabaseImportKind.GeoSite
                        GeoDataSourceSettingsDesign.Request.ImportCountryMmdb ->
                            GeoDatabaseImportKind.CountryMmdb
                        GeoDataSourceSettingsDesign.Request.ImportAsnMmdb ->
                            GeoDatabaseImportKind.AsnMmdb
                    }
                    val uri = startActivityForResult(
                        ActivityResultContracts.GetContent(),
                        "*/*",
                    )
                    when (importGeoDatabaseFromUri(uri, kind)) {
                        GeoDatabaseImportResult.BadExtension ->
                            MaterialAlertDialogBuilder(this@GeoDataSourceSettingsActivity)
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
        }
    }
}
