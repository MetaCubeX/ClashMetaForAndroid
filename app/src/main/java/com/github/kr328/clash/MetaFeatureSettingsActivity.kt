package com.github.kr328.clash

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.design.MetaFeatureSettingsDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.theme.YumeTheme
import com.github.kr328.clash.util.clashDir
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class MetaFeatureSettingsActivity : ComponentActivity() {
    private val scope = MainScope()
    private val validDatabaseExtensions = listOf(
        ".metadb", ".db", ".dat", ".mmdb"
    )
    private lateinit var configuration: ConfigurationOverride

    private var currentImportType: MetaImport? = null
    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val type = currentImportType
        if (uri != null && type != null) {
            importGeoFile(uri, type)
        }
        currentImportType = null
    }

    private lateinit var design: MetaFeatureSettingsDesign
    private var currentListEditorCallback: ((List<String>) -> Unit)? = null
    private val listEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val items = result.data?.getStringArrayListExtra(ListEditorActivity.RESULT_ITEMS)
                if (items != null && currentListEditorCallback != null) {
                    currentListEditorCallback?.invoke(items)
                    currentListEditorCallback = null
            }
        }
    }

    private fun pickAndImport(type: MetaImport) {
        currentImportType = type
        importLauncher.launch("*/*")
    }

    private fun launchListEditor(
        title: String,
        items: List<String>,
        validatorType: String,
        callback: (List<String>) -> Unit
    ) {
        currentListEditorCallback = callback
        val intent = ListEditorActivity.start(
            this@MetaFeatureSettingsActivity,
            title,
            ArrayList(items),
            validatorType
        )
        listEditorLauncher.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scope.launch {
            val config = withClash { queryOverride(Clash.OverrideSlot.Persist) } ?: ConfigurationOverride()
            configuration = config

            design = MetaFeatureSettingsDesign(
                this@MetaFeatureSettingsActivity,
                config,
                onConfigChange = { newConfig ->
                    configuration = newConfig
                }
            )

            withContext(Dispatchers.Main) {
                setContent {
                    YumeTheme {
                        design.Content()
                    }
                }
            }

            for (req in design.requests) {
                when (req) {
                    MetaFeatureSettingsDesign.Request.Close -> finish()
                    MetaFeatureSettingsDesign.Request.ImportGeoIp -> pickAndImport(MetaImport.GeoIp)
                    MetaFeatureSettingsDesign.Request.ImportGeoSite -> pickAndImport(MetaImport.GeoSite)
                    MetaFeatureSettingsDesign.Request.ImportCountry -> pickAndImport(MetaImport.Country)
                    MetaFeatureSettingsDesign.Request.ImportASN -> pickAndImport(MetaImport.ASN)
                    MetaFeatureSettingsDesign.Request.ResetOverride -> {
                        scope.launch {
                            try {
                                withClash { clearOverride(Clash.OverrideSlot.Persist) }
                                // 重置后重新加载配置
                                val newConfig =
                                    withClash { queryOverride(Clash.OverrideSlot.Persist) } ?: ConfigurationOverride()
                                configuration = newConfig
                                design.config = newConfig
                                runOnUiThread {
                                    Toast.makeText(this@MetaFeatureSettingsActivity, "重置成功", Toast.LENGTH_SHORT)
                                        .show()
                                    finish()
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MetaFeatureSettingsActivity,
                                        "重置失败: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }

                    MetaFeatureSettingsDesign.Request.Save -> {
                        scope.launch {
                            try {
                                withClash { patchOverride(Clash.OverrideSlot.Persist, design.config) }
                                runOnUiThread {
                                    Toast.makeText(this@MetaFeatureSettingsActivity, "保存成功", Toast.LENGTH_SHORT)
                                        .show()
                                    finish()
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MetaFeatureSettingsActivity,
                                        "保存失败: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }

                    MetaFeatureSettingsDesign.Request.EditHttpPorts -> {
                        launchListEditor(
                            "Sniff HTTP Ports",
                            design.config.sniffer.sniff.http.ports ?: emptyList(),
                            ListEditorActivity.VALIDATOR_NONE
                        ) { newItems ->
                            design.config = design.config.copy(
                                sniffer = design.config.sniffer.copy(
                                    sniff = design.config.sniffer.sniff.copy(
                                        http = design.config.sniffer.sniff.http.copy(ports = newItems)
                                    )
                                )
                            )
                            configuration = design.config
                        }
                    }

                    MetaFeatureSettingsDesign.Request.EditTlsPorts -> {
                        launchListEditor(
                            "Sniff TLS Ports",
                            design.config.sniffer.sniff.tls.ports ?: emptyList(),
                            ListEditorActivity.VALIDATOR_NONE
                        ) { newItems ->
                            design.config = design.config.copy(
                                sniffer = design.config.sniffer.copy(
                                    sniff = design.config.sniffer.sniff.copy(
                                        tls = design.config.sniffer.sniff.tls.copy(ports = newItems)
                                    )
                                )
                            )
                            configuration = design.config
                        }
                    }

                    MetaFeatureSettingsDesign.Request.EditQuicPorts -> {
                        launchListEditor(
                            "Sniff QUIC Ports",
                            design.config.sniffer.sniff.quic.ports ?: emptyList(),
                            ListEditorActivity.VALIDATOR_NONE
                        ) { newItems ->
                            design.config = design.config.copy(
                                sniffer = design.config.sniffer.copy(
                                    sniff = design.config.sniffer.sniff.copy(
                                        quic = design.config.sniffer.sniff.quic.copy(ports = newItems)
                                    )
                                )
                            )
                            configuration = design.config
                        }
                    }

                    MetaFeatureSettingsDesign.Request.EditForceDomains -> {
                        launchListEditor(
                            "强制域名",
                            design.config.sniffer.forceDomain ?: emptyList(),
                            ListEditorActivity.VALIDATOR_DOMAIN
                        ) { newItems ->
                            design.config = design.config.copy(
                                sniffer = design.config.sniffer.copy(forceDomain = newItems)
                            )
                            configuration = design.config
                        }
                    }

                    MetaFeatureSettingsDesign.Request.EditSkipDomains -> {
                        launchListEditor(
                            "跳过域名",
                            design.config.sniffer.skipDomain ?: emptyList(),
                            ListEditorActivity.VALIDATOR_DOMAIN
                        ) { newItems ->
                            design.config = design.config.copy(
                                sniffer = design.config.sniffer.copy(skipDomain = newItems)
                            )
                            configuration = design.config
                        }
                    }

                    MetaFeatureSettingsDesign.Request.EditSkipSrcAddresses -> {
                        launchListEditor(
                            "跳过源地址",
                            design.config.sniffer.skipSrcAddress ?: emptyList(),
                            ListEditorActivity.VALIDATOR_CIDR
                        ) { newItems ->
                            design.config = design.config.copy(
                                sniffer = design.config.sniffer.copy(skipSrcAddress = newItems)
                            )
                            configuration = design.config
                        }
                    }

                    MetaFeatureSettingsDesign.Request.EditSkipDstAddresses -> {
                        launchListEditor(
                            "跳过目标地址",
                            design.config.sniffer.skipDstAddress ?: emptyList(),
                            ListEditorActivity.VALIDATOR_CIDR
                        ) { newItems ->
                            design.config = design.config.copy(
                                sniffer = design.config.sniffer.copy(skipDstAddress = newItems)
                            )
                            configuration = design.config
                        }
                    }
                }
            }
        }
    }

    private fun importGeoFile(uri: Uri, importType: MetaImport) {
        val cursor = contentResolver.query(uri, null, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val displayName: String = if (columnIndex != -1) it.getString(columnIndex) else ""
                val ext = "." + displayName.substringAfterLast('.', "")

                if (ext == "." || !validDatabaseExtensions.contains(ext)) {
                    Toast.makeText(
                        this,
                        getString(
                            R.string.geofile_unknown_db_format_message,
                            validDatabaseExtensions.joinToString("/")
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                val outputFileName = when (importType) {
                    MetaImport.GeoIp -> "geoip$ext"
                    MetaImport.GeoSite -> "geosite$ext"
                    MetaImport.Country -> "country$ext"
                    MetaImport.ASN -> "ASN$ext"
                }

                scope.launch(Dispatchers.IO) {
                    val outputFile = File(clashDir, outputFileName)
                    contentResolver.openInputStream(uri).use { ins ->
                        FileOutputStream(outputFile).use { outs -> ins?.copyTo(outs) }
                    }
                }
                Toast.makeText(this, getString(R.string.geofile_imported, displayName), Toast.LENGTH_LONG).show()
                return
            }
        }
        Toast.makeText(this, R.string.geofile_import_failed, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

private enum class MetaImport { GeoIp, GeoSite, Country, ASN }