package com.github.kr328.clash

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.design.MetaFeatureSettingsDesign
import com.github.kr328.clash.util.GeoFileImport
import com.github.kr328.clash.util.clashDir
import com.github.kr328.clash.util.geoImportOutputName
import com.github.kr328.clash.util.importReplacingGeoFile
import com.github.kr328.clash.util.withClash
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.IOException
import com.github.kr328.clash.design.R


class MetaFeatureSettingsActivity : BaseActivity<MetaFeatureSettingsDesign>() {
    private var configuration: ConfigurationOverride? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        configuration = savedInstanceState?.readPersistOverride(PERSIST_OVERRIDE_STATE)
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        configuration?.let { outState.putParcelable(PERSIST_OVERRIDE_STATE, it) }
    }

    companion object {
        const val EXTRA_FOCUS_GEO = "focus_geo"
    }

    override suspend fun main() {
        val configuration = this.configuration ?: withClash {
            queryOverride(Clash.OverrideSlot.Persist)
        }.also { this.configuration = it }

        defer {
            withClash {
                patchOverride(Clash.OverrideSlot.Persist, configuration)
            }
        }

        val design = MetaFeatureSettingsDesign(
            this,
            configuration,
            intent.getBooleanExtra(EXTRA_FOCUS_GEO, false),
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
                            importGeoFile(uri, MetaFeatureSettingsDesign.Request.ImportGeoIp)
                        }
                        MetaFeatureSettingsDesign.Request.ImportGeoSite -> {
                            val uri = startActivityForResult(
                                ActivityResultContracts.GetContent(),
                                "*/*")
                            importGeoFile(uri, MetaFeatureSettingsDesign.Request.ImportGeoSite)
                        }
                        MetaFeatureSettingsDesign.Request.ImportCountry -> {
                            val uri = startActivityForResult(
                                ActivityResultContracts.GetContent(),
                                "*/*")
                            importGeoFile(uri, MetaFeatureSettingsDesign.Request.ImportCountry)
                        }
                        MetaFeatureSettingsDesign.Request.ImportASN -> {
                            val uri = startActivityForResult(
                                ActivityResultContracts.GetContent(),
                                "*/*")
                            importGeoFile(uri, MetaFeatureSettingsDesign.Request.ImportASN)
                        }
                    }
                }
            }
        }
    }

    private val validDatabaseExtensions = listOf(
        ".metadb", ".db", ".dat", ".mmdb"
    )

    private suspend fun importGeoFile(uri: Uri?, importType: MetaFeatureSettingsDesign.Request) {
        val cursor: Cursor? = uri?.let {
            contentResolver.query(it, null, null, null, null, null)
        }
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val displayName: String =
                    if (columnIndex != -1) it.getString(columnIndex) else "";
                val ext = "." + displayName.substringAfterLast(".")

                if (!validDatabaseExtensions.contains(ext)) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.geofile_unknown_db_format)
                        .setMessage(getString(R.string.geofile_unknown_db_format_message,
                            validDatabaseExtensions.joinToString("/")))
                        .setPositiveButton(R.string.ok) { _, _ -> }
                        .show()
                    return
                }
                val kind = when (importType) {
                    MetaFeatureSettingsDesign.Request.ImportGeoIp -> GeoFileImport.GeoIp
                    MetaFeatureSettingsDesign.Request.ImportGeoSite -> GeoFileImport.GeoSite
                    MetaFeatureSettingsDesign.Request.ImportCountry -> GeoFileImport.Country
                    MetaFeatureSettingsDesign.Request.ImportASN -> GeoFileImport.Asn
                    MetaFeatureSettingsDesign.Request.ResetOverride -> null
                }
                val outputFileName = kind?.let { geoImportOutputName(it, ext) }
                if (outputFileName == null) {
                    Toast.makeText(this, R.string.geofile_import_failed, Toast.LENGTH_LONG).show()
                    return
                }

                try {
                    withContext(Dispatchers.IO) {
                        val input = contentResolver.openInputStream(uri)
                            ?: throw IOException("empty stream")
                        input.use {
                            importReplacingGeoFile(clashDir, outputFileName, it)
                        }
                    }
                    Toast.makeText(this, getString(R.string.geofile_imported, displayName),
                        Toast.LENGTH_LONG).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.geofile_import_failed, Toast.LENGTH_LONG).show()
                }
                return
            }
        }
        Toast.makeText(this, R.string.geofile_import_failed, Toast.LENGTH_LONG).show()
    }
}