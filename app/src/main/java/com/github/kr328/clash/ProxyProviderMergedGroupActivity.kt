package com.github.kr328.clash

import android.os.Bundle
import android.widget.EditText
import androidx.core.view.WindowCompat
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.design.ProxyProviderMergedGroupDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.util.ProxyGroupsYamlPreview
import com.github.kr328.clash.service.util.ProxyProvidersUi
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.util.showYamlPreview
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.util.UUID

/**
 * Adds a **select** proxy-group with `use: [sub1, sub2, …]` so the node you pick is the one used
 * (url-test would often fall back to the fastest provider for the health-check URL).
 */
class ProxyProviderMergedGroupActivity : BaseActivity<ProxyProviderMergedGroupDesign>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override suspend fun main() {
        val uuid: UUID = intent.uuid ?: return finish()
        val design = ProxyProviderMergedGroupDesign(this)
        setContentDesign(design)

        refreshSummary(uuid, design)

        while (isActive) {
            select<Unit> {
                design.requests.onReceive { req ->
                    when (req) {
                        ProxyProviderMergedGroupDesign.Request.AddMergedSelectGroup ->
                            launch { addMergedSelectGroup(uuid, design) }
                        is ProxyProviderMergedGroupDesign.Request.RemoveMergedGroup ->
                            launch { confirmRemoveMergedGroup(uuid, design, req.name) }
                    }
                }
            }
        }
    }

    private suspend fun refreshSummary(uuid: UUID, design: ProxyProviderMergedGroupDesign) {
        val yaml = withProfile { readProxyProvidersYaml(uuid) }
        val labelsJson = withProfile { readProxyProviderLabelsJson(uuid) }
        val labels = ProxyProvidersUi.parseLabelsJson(labelsJson)
        val rows = ProxyProvidersUi.parseRows(yaml, labels)
        val configYaml = withProfile { readImportedConfigYaml(uuid) }.orEmpty()
        val mergedNames = if (configYaml.isBlank()) {
            emptyList()
        } else {
            runCatching { Clash.parseProfileSnapshotFromYaml(configYaml) }
                .map { ProxyGroupsYamlPreview.listGroupNamesWithUse(it) }
                .getOrElse { emptyList() }
        }
        withContext(Dispatchers.Main) {
            if (rows.isEmpty()) {
                design.summaryKeys.text = getString(R.string.proxy_providers_keys_empty)
            } else {
                design.summaryKeys.text = rows.mapIndexed { i, r ->
                    val key = "sub${i + 1}"
                    val label = r.title.trim().ifEmpty { key }
                    getString(R.string.proxy_providers_key_line, key, label)
                }.joinToString("\n")
            }
            design.setExistingGroups(mergedNames)
        }
    }

    private suspend fun confirmRemoveMergedGroup(uuid: UUID, design: ProxyProviderMergedGroupDesign, name: String) {
        val ok = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Boolean> { cont ->
                MaterialAlertDialogBuilder(this@ProxyProviderMergedGroupActivity)
                    .setTitle(R.string.proxy_providers_merged_remove)
                    .setMessage(getString(R.string.proxy_providers_merged_remove_confirm, name))
                    .setPositiveButton(R.string.ok) { _, _ -> cont.resume(true) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .setOnDismissListener {
                        if (!cont.isCompleted) cont.resume(false)
                    }
                    .show()
            }
        }
        if (!ok) return
        try {
            val preview = withProfile { previewRemoveProxyGroup(uuid, name) }
            showYamlPreview(preview) {
                design.showToast(R.string.proxy_providers_merged_removed_ok, ToastDuration.Long)
                refreshSummary(uuid, design)
            }
        } catch (e: Exception) {
            design.showExceptionToast(e)
        }
    }

    private suspend fun addMergedSelectGroup(uuid: UUID, design: ProxyProviderMergedGroupDesign) {
        val yaml = withProfile { readProxyProvidersYaml(uuid) }
        val labelsJson = withProfile { readProxyProviderLabelsJson(uuid) }
        val labels = ProxyProvidersUi.parseLabelsJson(labelsJson)
        val rows = ProxyProvidersUi.parseRows(yaml, labels).filter { row ->
            val u = row.url.trim()
            u.isNotEmpty() && (u.startsWith("http://") || u.startsWith("https://"))
        }
        if (rows.isEmpty()) {
            design.showToast(R.string.proxy_providers_relay_need_rows, ToastDuration.Long)
            return
        }
        if (yaml.isNullOrBlank() || !yaml.contains("sub1")) {
            design.showToast(R.string.proxy_providers_relay_save_first, ToastDuration.Long)
            return
        }
        val keys = rows.indices.map { i -> "sub${i + 1}" }
        val name = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String?> { cont ->
                val edit = EditText(this@ProxyProviderMergedGroupActivity).apply {
                    hint = context.getString(R.string.proxy_providers_relay_dialog_hint)
                    setText("MainGroup")
                    setPadding(48, 32, 48, 16)
                }
                val dialog = MaterialAlertDialogBuilder(this@ProxyProviderMergedGroupActivity)
                    .setTitle(R.string.proxy_providers_relay_dialog_title)
                    .setView(edit)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        cont.resume(edit.text?.toString()?.trim().orEmpty())
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .setOnDismissListener {
                        if (!cont.isCompleted) cont.resume(null)
                    }
                    .show()
                cont.invokeOnCancellation { dialog.dismiss() }
            }
        }
        if (name == null) return
        if (name.isBlank()) {
            design.showToast(R.string.should_not_be_blank, ToastDuration.Short)
            return
        }
        try {
            val preview = withProfile { previewAppendRelayProxyGroup(uuid, name, keys) }
            showYamlPreview(preview) {
                design.showToast(R.string.proxy_providers_merged_select_ok, ToastDuration.Long)
                refreshSummary(uuid, design)
            }
        } catch (e: Exception) {
            design.showExceptionToast(e)
        }
    }
}
