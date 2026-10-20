package com.github.kr328.clash.design

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.adapter.EditableTextListAdapter
import com.github.kr328.clash.design.adapter.EditableTextMapAdapter
import com.github.kr328.clash.design.databinding.DialogEditableMapTextFieldBinding
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ios.OverrideSettingsScreen
import com.github.kr328.clash.design.ios.SettingGroup
import com.github.kr328.clash.design.ios.SettingRow
import com.github.kr328.clash.design.ios.iosConfirm
import com.github.kr328.clash.design.preference.EditableListOverlayResult
import com.github.kr328.clash.design.preference.NullableTextAdapter
import com.github.kr328.clash.design.preference.TextAdapter
import com.github.kr328.clash.design.preference.requestEditableListOverlay
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class OverrideSettingsDesign(
    context: Context,
    private val configuration: ConfigurationOverride,
) : Design<OverrideSettingsDesign.Request>(context) {
    enum class Request {
        ResetOverride
    }

    private val booleanValues: Array<Boolean?> = arrayOf(null, true, false)
    private val booleanTexts: Array<Int> = arrayOf(
        R.string.dont_modify,
        R.string.enabled,
        R.string.disabled,
    )

    private val dnsStrategyValues: Array<Boolean?> = arrayOf(null, true, false)
    private val dnsStrategyTexts: Array<Int> = arrayOf(
        R.string.dont_modify,
        R.string.force_enable,
        R.string.use_built_in,
    )

    private val modeValues: Array<TunnelState.Mode?> = arrayOf(
        null,
        TunnelState.Mode.Direct,
        TunnelState.Mode.Global,
        TunnelState.Mode.Rule,
    )
    private val modeTexts: Array<Int> = arrayOf(
        R.string.dont_modify,
        R.string.direct_mode,
        R.string.global_mode,
        R.string.rule_mode,
    )

    private val logLevelValues: Array<LogMessage.Level?> = arrayOf(
        null,
        LogMessage.Level.Info,
        LogMessage.Level.Warning,
        LogMessage.Level.Error,
        LogMessage.Level.Debug,
        LogMessage.Level.Silent,
    )
    private val logLevelTexts: Array<Int> = arrayOf(
        R.string.dont_modify,
        R.string.info,
        R.string.warning,
        R.string.error,
        R.string.debug,
        R.string.silent,
    )

    private val enhancedModeValues: Array<ConfigurationOverride.DnsEnhancedMode?> = arrayOf(
        null,
        ConfigurationOverride.DnsEnhancedMode.None,
        ConfigurationOverride.DnsEnhancedMode.FakeIp,
        ConfigurationOverride.DnsEnhancedMode.Mapping,
    )
    private val enhancedModeTexts: Array<Int> = arrayOf(
        R.string.dont_modify,
        R.string.disabled,
        R.string.fakeip,
        R.string.mapping,
    )

    private val filterModeValues: Array<ConfigurationOverride.FilterMode?> = arrayOf(
        null,
        ConfigurationOverride.FilterMode.BlackList,
        ConfigurationOverride.FilterMode.WhiteList,
        ConfigurationOverride.FilterMode.Rule,
    )
    private val filterModeTexts: Array<Int> = arrayOf(
        R.string.dont_modify,
        R.string.blacklist,
        R.string.whitelist,
        R.string.rule,
    )

    private var insets by mutableStateOf(Insets.EMPTY)
    private var groups by mutableStateOf<List<SettingGroup>>(emptyList())

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val title = (context as? Activity)?.title?.toString()?.ifBlank { null }
                ?: context.getString(R.string.override)
            ClashMetaXTheme {
                OverrideSettingsScreen(
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
        // null 和 true 仍可编辑。只有 false（使用内置）禁用其余 DNS 行。
        val dnsEditable = configuration.dns.enable != false
        return listOf(
            SettingGroup(
                title = text(R.string.general),
                rows = listOf(
                    portRow(R.string.http_port, { configuration.httpPort }) {
                        configuration.httpPort = it
                    },
                    portRow(R.string.socks_port, { configuration.socksPort }) {
                        configuration.socksPort = it
                    },
                    portRow(R.string.redirect_port, { configuration.redirectPort }) {
                        configuration.redirectPort = it
                    },
                    portRow(R.string.tproxy_port, { configuration.tproxyPort }) {
                        configuration.tproxyPort = it
                    },
                    portRow(R.string.mixed_port, { configuration.mixedPort }) {
                        configuration.mixedPort = it
                    },
                    listRow(
                        R.string.authentication,
                        { configuration.authentication },
                        { configuration.authentication = it },
                    ),
                    choice(
                        R.string.allow_lan,
                        configuration.allowLan,
                        booleanValues,
                        booleanTexts,
                    ) { configuration.allowLan = it },
                    choice(
                        R.string.ipv6,
                        configuration.ipv6,
                        booleanValues,
                        booleanTexts,
                    ) { configuration.ipv6 = it },
                    stringRow(
                        R.string.bind_address,
                        R.string.default_,
                        { configuration.bindAddress },
                        { configuration.bindAddress = it },
                    ),
                    stringRow(
                        R.string.external_controller,
                        R.string.default_,
                        { configuration.externalController },
                        { configuration.externalController = it },
                    ),
                    stringRow(
                        R.string.external_controller_tls,
                        R.string.default_,
                        { configuration.externalControllerTLS },
                        { configuration.externalControllerTLS = it },
                    ),
                    listRow(
                        R.string.allow_origins,
                        { configuration.externalControllerCors.allowOrigins },
                        { configuration.externalControllerCors.allowOrigins = it },
                    ),
                    choice(
                        R.string.allow_private_network,
                        configuration.externalControllerCors.allowPrivateNetwork,
                        booleanValues,
                        booleanTexts,
                    ) { configuration.externalControllerCors.allowPrivateNetwork = it },
                    secretRow(),
                    choice(
                        R.string.mode,
                        configuration.mode,
                        modeValues,
                        modeTexts,
                    ) { configuration.mode = it },
                    choice(
                        R.string.log_level,
                        configuration.logLevel,
                        logLevelValues,
                        logLevelTexts,
                    ) { configuration.logLevel = it },
                    mapRow(
                        R.string.hosts,
                        { configuration.hosts },
                        { configuration.hosts = it },
                    ),
                ),
            ),
            SettingGroup(
                title = text(R.string.dns),
                rows = listOf(
                    choice(
                        R.string.strategy,
                        configuration.dns.enable,
                        dnsStrategyValues,
                        dnsStrategyTexts,
                    ) { configuration.dns.enable = it },
                    choice(
                        R.string.prefer_h3,
                        configuration.dns.preferH3,
                        booleanValues,
                        booleanTexts,
                        dnsEditable,
                    ) { configuration.dns.preferH3 = it },
                    stringRow(
                        R.string.listen,
                        R.string.disabled,
                        { configuration.dns.listen },
                        { configuration.dns.listen = it },
                        dnsEditable,
                    ),
                    choice(
                        R.string.append_system_dns,
                        configuration.app.appendSystemDns,
                        booleanValues,
                        booleanTexts,
                        dnsEditable,
                    ) { configuration.app.appendSystemDns = it },
                    choice(
                        R.string.ipv6,
                        configuration.dns.ipv6,
                        booleanValues,
                        booleanTexts,
                        dnsEditable,
                    ) { configuration.dns.ipv6 = it },
                    choice(
                        R.string.use_hosts,
                        configuration.dns.useHosts,
                        booleanValues,
                        booleanTexts,
                        dnsEditable,
                    ) { configuration.dns.useHosts = it },
                    choice(
                        R.string.enhanced_mode,
                        configuration.dns.enhancedMode,
                        enhancedModeValues,
                        enhancedModeTexts,
                        dnsEditable,
                    ) { configuration.dns.enhancedMode = it },
                    listRow(
                        R.string.name_server,
                        { configuration.dns.nameServer },
                        { configuration.dns.nameServer = it },
                        dnsEditable,
                    ),
                    listRow(
                        R.string.fallback,
                        { configuration.dns.fallback },
                        { configuration.dns.fallback = it },
                        dnsEditable,
                    ),
                    listRow(
                        R.string.default_name_server,
                        { configuration.dns.defaultServer },
                        { configuration.dns.defaultServer = it },
                        dnsEditable,
                    ),
                    listRow(
                        R.string.fakeip_filter,
                        { configuration.dns.fakeIpFilter },
                        { configuration.dns.fakeIpFilter = it },
                        dnsEditable,
                    ),
                    choice(
                        R.string.fakeip_filter_mode,
                        configuration.dns.fakeIPFilterMode,
                        filterModeValues,
                        filterModeTexts,
                        dnsEditable,
                    ) { configuration.dns.fakeIPFilterMode = it },
                    choice(
                        R.string.geoip_fallback,
                        configuration.dns.fallbackFilter.geoIp,
                        booleanValues,
                        booleanTexts,
                        dnsEditable,
                    ) { configuration.dns.fallbackFilter.geoIp = it },
                    stringRow(
                        R.string.geoip_fallback_code,
                        R.string.raw_cn,
                        { configuration.dns.fallbackFilter.geoIpCode },
                        { configuration.dns.fallbackFilter.geoIpCode = it },
                        dnsEditable,
                    ),
                    listRow(
                        R.string.domain_fallback,
                        { configuration.dns.fallbackFilter.domain },
                        { configuration.dns.fallbackFilter.domain = it },
                        dnsEditable,
                    ),
                    listRow(
                        R.string.ipcidr_fallback,
                        { configuration.dns.fallbackFilter.ipcidr },
                        { configuration.dns.fallbackFilter.ipcidr = it },
                        dnsEditable,
                    ),
                    mapRow(
                        R.string.name_server_policy,
                        { configuration.dns.nameserverPolicy },
                        { configuration.dns.nameserverPolicy = it },
                        dnsEditable,
                    ),
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

    private fun portRow(
        @StringRes title: Int,
        read: () -> Int?,
        write: (Int?) -> Unit,
    ) = valueRow(title, portSummary(read())) {
        editNullable(title, NullableTextAdapter.Port, read, write)
    }

    private fun stringRow(
        @StringRes title: Int,
        @StringRes empty: Int,
        read: () -> String?,
        write: (String?) -> Unit,
        enabled: Boolean = true,
    ) = valueRow(title, stringSummary(read(), empty), enabled) {
        editNullable(title, NullableTextAdapter.String, read, write)
    }

    private fun secretRow() = valueRow(R.string.secret, secretSummary(configuration.secret)) {
        editNullable(
            R.string.secret,
            NullableTextAdapter.String,
            { configuration.secret },
        ) { configuration.secret = it }
    }

    private fun listRow(
        @StringRes title: Int,
        read: () -> List<String>?,
        write: (List<String>?) -> Unit,
        enabled: Boolean = true,
    ) = valueRow(title, elements(read()?.size), enabled) {
        editList(title, read, write)
    }

    private fun mapRow(
        @StringRes title: Int,
        read: () -> Map<String, String>?,
        write: (Map<String, String>?) -> Unit,
        enabled: Boolean = true,
    ) = valueRow(title, elements(read()?.size), enabled) {
        editMap(title, read, write)
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

    // NullableTextAdapter.Port：null 不修改，空或小于等于 0 显示已禁用。
    private fun portSummary(value: Int?): String {
        val shown = NullableTextAdapter.Port.from(value)
        return when {
            shown == null -> text(R.string.dont_modify)
            shown.isEmpty() -> text(R.string.disabled)
            else -> shown
        }
    }

    private fun stringSummary(value: String?, @StringRes empty: Int): String {
        val shown = NullableTextAdapter.String.from(value)
        return when {
            shown == null -> text(R.string.dont_modify)
            shown.isEmpty() -> text(empty)
            else -> shown
        }
    }

    // null 不修改，空字符串默认，非空只显示已设置。编辑器仍预填原文。
    private fun secretSummary(value: String?): String = when (value) {
        null -> text(R.string.dont_modify)
        "" -> text(R.string.default_)
        else -> text(R.string.webdav_password_set)
    }

    private fun elements(size: Int?): String = when (size) {
        null -> text(R.string.dont_modify)
        0 -> text(R.string.empty)
        else -> context.getString(R.string.format_elements, size)
    }

    private fun text(@StringRes id: Int): String = context.getString(id)

    private fun <T> editNullable(
        @StringRes titleRes: Int,
        adapter: NullableTextAdapter<T>,
        read: () -> T,
        write: (T) -> Unit,
    ) {
        launch(Dispatchers.Main) {
            val title = context.getText(titleRes)
            val initial = withContext(Dispatchers.IO) { adapter.from(read()) }
            val entered = context.requestModelTextInput(
                initial = initial,
                title = title,
                reset = context.getText(R.string.reset),
                hint = title,
                preserveUnedited = true,
            )
            withContext(Dispatchers.IO) {
                write(adapter.to(entered))
            }
            publish()
        }
    }

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

    private fun editMap(
        @StringRes titleRes: Int,
        read: () -> Map<String, String>?,
        write: (Map<String, String>?) -> Unit,
    ) {
        launch(Dispatchers.Main) {
            val title = context.getText(titleRes)
            val initial = withContext(Dispatchers.IO) { read() }
            val updated = requestTextMap(initial, title)
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

    private suspend fun requestTextMap(
        initialValue: Map<String, String>?,
        title: CharSequence,
    ): Map<String, String>? {
        val editableValue = withContext(Dispatchers.Default) {
            initialValue?.map { it.key to it.value }?.toMutableList() ?: mutableListOf()
        }
        val recyclerAdapter = EditableTextMapAdapter(
            context,
            editableValue,
            TextAdapter.String,
            TextAdapter.String,
        )
        val result = requestEditableListOverlay(context, recyclerAdapter, title) {
            val created = requestMapEntry(title)
            if (created != null) {
                recyclerAdapter.addElement(created.first, created.second)
            }
        }
        return when (result) {
            EditableListOverlayResult.Cancel -> initialValue
            EditableListOverlayResult.Apply -> recyclerAdapter.values.toMap()
            EditableListOverlayResult.Reset -> null
        }
    }

    private suspend fun requestMapEntry(title: CharSequence): Pair<String, String>? {
        return suspendCancellableCoroutine { ctx ->
            val binding = DialogEditableMapTextFieldBinding
                .inflate(context.layoutInflater, context.root, false)
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setNegativeButton(R.string.cancel) { _, _ ->
                    if (!ctx.isCompleted) {
                        ctx.resume(null)
                    }
                }
                .setPositiveButton(R.string.ok) { _, _ -> }
                .setView(binding.root)
                .create()
            dialog.setOnCancelListener {
                if (!ctx.isCompleted) {
                    ctx.resume(null)
                }
            }
            dialog.setOnDismissListener {
                if (!ctx.isCompleted) {
                    ctx.resume(null)
                }
            }
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val key = binding.keyView.text?.toString()?.trim() ?: ""
                val value = binding.valueView.text?.toString()?.trim() ?: ""
                if (key.isEmpty() || value.isEmpty()) {
                    return@setOnClickListener
                }
                if (!ctx.isCompleted) {
                    ctx.resume(key to value)
                }
                dialog.dismiss()
            }
        }
    }
}
