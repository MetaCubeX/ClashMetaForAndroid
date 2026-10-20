package com.github.kr328.clash.design

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.getSystemService
import androidx.core.widget.doOnTextChanged
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.design.adapter.EditableTextListAdapter
import com.github.kr328.clash.design.databinding.DialogAgeKeyHelperBinding
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ios.MetaFeatureSettingsScreen
import com.github.kr328.clash.design.ios.SettingGroup
import com.github.kr328.clash.design.ios.SettingRow
import com.github.kr328.clash.design.ios.iosConfirm
import com.github.kr328.clash.design.preference.EditableListOverlayResult
import com.github.kr328.clash.design.preference.TextAdapter
import com.github.kr328.clash.design.preference.requestEditableListOverlay
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MetaFeatureSettingsDesign(
    context: Context,
    private val configuration: ConfigurationOverride,
) : Design<MetaFeatureSettingsDesign.Request>(context) {
    enum class Request {
        ResetOverride, ImportGeoIp, ImportGeoSite, ImportCountry, ImportASN
    }

    private val booleanValues: Array<Boolean?> = arrayOf(null, true, false)
    private val booleanTexts: Array<Int> = arrayOf(
        R.string.dont_modify,
        R.string.enabled,
        R.string.disabled,
    )

    private val findProcessValues: Array<ConfigurationOverride.FindProcessMode?> = arrayOf(
        null,
        ConfigurationOverride.FindProcessMode.Off,
        ConfigurationOverride.FindProcessMode.Strict,
        ConfigurationOverride.FindProcessMode.Always,
    )
    private val findProcessTexts: Array<Int> = arrayOf(
        R.string.dont_modify,
        R.string.off,
        R.string.strict,
        R.string.always,
    )

    private val snifferStrategyValues: Array<Boolean?> = arrayOf(null, true, false)
    private val snifferStrategyTexts: Array<Int> = arrayOf(
        R.string.dont_modify,
        R.string.enabled,
        R.string.disabled,
    )

    private var insets by mutableStateOf(Insets.EMPTY)
    private var groups by mutableStateOf<List<SettingGroup>>(emptyList())

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val title = (context as? Activity)?.title?.toString()?.ifBlank { null }
                ?: context.getString(R.string.meta_features)
            ClashMetaXTheme {
                MetaFeatureSettingsScreen(
                    insets = insets,
                    title = title,
                    groups = groups,
                    onBack = {
                        (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
                    },
                    onReset = { requestClear() },
                )
            }
        }
    }

    suspend fun requestResetConfirm(): Boolean {
        return iosConfirm(
            context = context,
            title = context.getString(R.string.reset_override_settings),
            message = context.getString(R.string.reset_override_settings_message),
            confirm = context.getString(R.string.ok),
            destructive = true,
        )
    }

    fun requestClear() {
        requests.trySend(Request.ResetOverride)
    }

    init {
        if (context is AppCompatActivity) {
            context.window.decorView.setOnInsertsChangedListener {
                if (surface.insets != it) surface.insets = it
                insets = it
            }
        }
        publish()
    }

    private fun publish() {
        groups = snapshot()
    }

    private fun snapshot(): List<SettingGroup> {
        // 只有 false（已禁用）关掉其余嗅探行，第一次进入也按这个值。
        val sniffEditable = configuration.sniffer.enable != false
        return listOf(
            SettingGroup(
                title = text(R.string.age_key_category),
                rows = listOf(
                    valueRow(
                        R.string.age_key_type_x25519,
                        text(R.string.age_key_generate_summary),
                    ) { requestAgeKeyHelper(hybrid = false) },
                    valueRow(
                        R.string.age_key_type_hybrid,
                        text(R.string.age_key_generate_summary),
                    ) { requestAgeKeyHelper(hybrid = true) },
                ),
            ),
            SettingGroup(
                title = text(R.string.settings),
                rows = listOf(
                    choice(
                        R.string.unified_delay,
                        configuration.unifiedDelay,
                        booleanValues,
                        booleanTexts,
                    ) { configuration.unifiedDelay = it },
                    choice(
                        R.string.geodata_mode,
                        configuration.geodataMode,
                        booleanValues,
                        booleanTexts,
                    ) { configuration.geodataMode = it },
                    choice(
                        R.string.tcp_concurrent,
                        configuration.tcpConcurrent,
                        booleanValues,
                        booleanTexts,
                    ) { configuration.tcpConcurrent = it },
                    choice(
                        R.string.find_process_mode,
                        configuration.findProcessMode,
                        findProcessValues,
                        findProcessTexts,
                    ) { configuration.findProcessMode = it },
                ),
            ),
            SettingGroup(
                title = text(R.string.sniffer_setting),
                rows = listOf(
                    choice(
                        R.string.sniff_strategy,
                        configuration.sniffer.enable,
                        snifferStrategyValues,
                        snifferStrategyTexts,
                    ) { configuration.sniffer.enable = it },
                    listRow(
                        R.string.sniff_http_ports,
                        { configuration.sniffer.sniff.http.ports },
                        { configuration.sniffer.sniff.http.ports = it },
                        sniffEditable,
                    ),
                    choice(
                        R.string.sniff_http_override_destination,
                        configuration.sniffer.sniff.http.overrideDestination,
                        booleanValues,
                        booleanTexts,
                        sniffEditable,
                    ) { configuration.sniffer.sniff.http.overrideDestination = it },
                    listRow(
                        R.string.sniff_tls_ports,
                        { configuration.sniffer.sniff.tls.ports },
                        { configuration.sniffer.sniff.tls.ports = it },
                        sniffEditable,
                    ),
                    choice(
                        R.string.sniff_tls_override_destination,
                        configuration.sniffer.sniff.tls.overrideDestination,
                        booleanValues,
                        booleanTexts,
                        sniffEditable,
                    ) { configuration.sniffer.sniff.tls.overrideDestination = it },
                    listRow(
                        R.string.sniff_quic_ports,
                        { configuration.sniffer.sniff.quic.ports },
                        { configuration.sniffer.sniff.quic.ports = it },
                        sniffEditable,
                    ),
                    choice(
                        R.string.sniff_quic_override_destination,
                        configuration.sniffer.sniff.quic.overrideDestination,
                        booleanValues,
                        booleanTexts,
                        sniffEditable,
                    ) { configuration.sniffer.sniff.quic.overrideDestination = it },
                    choice(
                        R.string.force_dns_mapping,
                        configuration.sniffer.forceDnsMapping,
                        booleanValues,
                        booleanTexts,
                        sniffEditable,
                    ) { configuration.sniffer.forceDnsMapping = it },
                    choice(
                        R.string.parse_pure_ip,
                        configuration.sniffer.parsePureIp,
                        booleanValues,
                        booleanTexts,
                        sniffEditable,
                    ) { configuration.sniffer.parsePureIp = it },
                    choice(
                        R.string.override_destination,
                        configuration.sniffer.overrideDestination,
                        booleanValues,
                        booleanTexts,
                        sniffEditable,
                    ) { configuration.sniffer.overrideDestination = it },
                    listRow(
                        R.string.force_domain,
                        { configuration.sniffer.forceDomain },
                        { configuration.sniffer.forceDomain = it },
                        sniffEditable,
                    ),
                    listRow(
                        R.string.skip_domain,
                        { configuration.sniffer.skipDomain },
                        { configuration.sniffer.skipDomain = it },
                        sniffEditable,
                    ),
                    listRow(
                        R.string.skip_src_address,
                        { configuration.sniffer.skipSrcAddress },
                        { configuration.sniffer.skipSrcAddress = it },
                        sniffEditable,
                    ),
                    listRow(
                        R.string.skip_dst_address,
                        { configuration.sniffer.skipDstAddress },
                        { configuration.sniffer.skipDstAddress = it },
                        sniffEditable,
                    ),
                ),
            ),
            SettingGroup(
                title = text(R.string.geox_files),
                rows = listOf(
                    valueRow(R.string.import_geoip_file, text(R.string.press_to_import)) {
                        requests.trySend(Request.ImportGeoIp)
                    },
                    valueRow(R.string.import_geosite_file, text(R.string.press_to_import)) {
                        requests.trySend(Request.ImportGeoSite)
                    },
                    valueRow(R.string.import_country_file, text(R.string.press_to_import)) {
                        requests.trySend(Request.ImportCountry)
                    },
                    valueRow(R.string.import_asn_file, text(R.string.press_to_import)) {
                        requests.trySend(Request.ImportASN)
                    },
                ),
            ),
        )
    }

    private fun <T> choice(
        @StringRes title: Int,
        value: T,
        values: Array<T>,
        texts: Array<Int>,
        enabled: Boolean = true,
        write: (T) -> Unit,
    ): SettingRow {
        val index = values.indexOf(value)
        val labels = texts.map { text(it) }
        return SettingRow(
            title = text(title),
            summary = labels.getOrNull(index).orEmpty(),
            enabled = enabled,
            selectable = true,
            options = labels,
            selected = index,
            onSelect = { picked ->
                if (picked in values.indices) {
                    val next = values[picked]
                    launch(Dispatchers.Main) {
                        withContext(Dispatchers.IO) { write(next) }
                        publish()
                    }
                }
            },
        )
    }

    private fun listRow(
        @StringRes title: Int,
        read: () -> List<String>?,
        write: (List<String>?) -> Unit,
        enabled: Boolean = true,
    ) = valueRow(title, elements(read()?.size), enabled) {
        editList(title, read, write)
    }

    private fun valueRow(
        @StringRes title: Int,
        summary: String,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) = SettingRow(
        title = text(title),
        summary = summary,
        enabled = enabled,
        onClick = onClick,
    )

    private fun elements(size: Int?): String = when (size) {
        null -> text(R.string.dont_modify)
        0 -> text(R.string.empty)
        else -> context.getString(R.string.format_elements, size)
    }

    private fun text(@StringRes id: Int): String = context.getString(id)

    private fun editList(
        @StringRes titleRes: Int,
        read: () -> List<String>?,
        write: (List<String>?) -> Unit,
    ) {
        launch(Dispatchers.Main) {
            val title = context.getText(titleRes)
            val initial = withContext(Dispatchers.IO) { read() }
            val updated = requestTextList(initial, title)
            withContext(Dispatchers.IO) { write(updated) }
            publish()
        }
    }

    private suspend fun requestTextList(
        initialValue: List<String>?,
        title: CharSequence,
    ): List<String>? {
        val recyclerAdapter = EditableTextListAdapter(
            context,
            initialValue?.toMutableList() ?: mutableListOf(),
            TextAdapter.String,
        )
        val result = requestEditableListOverlay(context, recyclerAdapter, title) {
            val entered = context.requestModelTextInput(
                initial = "",
                title = title,
                hint = title,
            )
            if (entered.isNotBlank()) {
                recyclerAdapter.addElement(entered)
            }
        }
        return when (result) {
            EditableListOverlayResult.Cancel -> initialValue
            EditableListOverlayResult.Apply -> recyclerAdapter.values
            EditableListOverlayResult.Reset -> null
        }
    }

    private fun requestAgeKeyHelper(hybrid: Boolean) {
        launch(Dispatchers.Main) {
            val binding = DialogAgeKeyHelperBinding
                .inflate(context.layoutInflater, context.root, false)
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(if (hybrid) R.string.age_key_type_hybrid else R.string.age_key_type_x25519)
                .setView(binding.root)
                .create()

            fun copy(label: String, value: String) {
                if (value.isBlank())
                    return

                val data = ClipData.newPlainText(label, value)
                context.getSystemService<ClipboardManager>()?.setPrimaryClip(data)

                launch { showToast(R.string.copied, ToastDuration.Short) }
            }

            fun patchSecretKeyState() {
                val secretKey = binding.secretKeyView.text?.toString() ?: ""
                val valid = secretKey.isBlank() || Clash.veritySecretKeys(secretKey)

                binding.secretKeyLayout.error = if (valid) null else context.getText(R.string.age_secret_key_error)
            }

            fun patchPublicKeyState() {
                val publicKey = binding.publicKeyView.text?.toString() ?: ""
                val valid = publicKey.isBlank() || Clash.verityPublicKeys(publicKey)

                binding.publicKeyLayout.error = if (valid) null else context.getText(R.string.age_public_key_error)
            }

            dialog.setOnShowListener {
                binding.secretKeyView.doOnTextChanged { _, _, _, _ -> patchSecretKeyState() }
                binding.publicKeyView.doOnTextChanged { _, _, _, _ -> patchPublicKeyState() }

                binding.generateView.setOnClickListener {
                    val keyPair = if (hybrid) {
                        Clash.genHybridKeyPair()
                    } else {
                        Clash.genX25519KeyPair()
                    }

                    binding.secretKeyView.setText(keyPair.secretKey)
                    binding.publicKeyView.setText(keyPair.publicKey)
                }

                binding.toPublicKeyView.setOnClickListener {
                    val publicKey = Clash.toPublicKeys(binding.secretKeyView.text?.toString() ?: "")
                        .firstOrNull()
                        ?: ""

                    binding.publicKeyView.setText(publicKey)
                }

                binding.copySecretKeyView.setOnClickListener {
                    copy("age_secret_key", binding.secretKeyView.text?.toString() ?: "")
                }

                binding.copyPublicKeyView.setOnClickListener {
                    copy("age_public_key", binding.publicKeyView.text?.toString() ?: "")
                }
            }

            dialog.show()
        }
    }
}
