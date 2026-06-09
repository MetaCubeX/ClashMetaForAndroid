package com.github.kr328.clash

import android.os.Bundle
import androidx.core.view.WindowCompat
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProfileSnapshot
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.TunnelsSettingsDesign
import com.github.kr328.clash.service.model.YamlPreview
import com.github.kr328.clash.service.util.ProxyGroupsYamlPreview
import com.github.kr328.clash.service.util.TunnelsConfig
import com.github.kr328.clash.service.util.TunnelsValidator
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID

/**
 * Per-profile `tunnels:` editor over the active profile. Reads via engine
 * snapshot, writes through the preview pipeline, and toggles the per-profile
 * managed marker that drives subscription-refresh preservation. Master toggle
 * OFF tears the block down cleanly.
 */
class TunnelsSettingsActivity : BaseActivity<TunnelsSettingsDesign>() {
    private val json = Json { ignoreUnknownKeys = true }

    private var uuid: UUID? = null
    private var profileName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override suspend fun main() {
        val design = TunnelsSettingsDesign(this)
        setContentDesign(design)

        val active = withProfile { queryActive() }
        if (active == null) {
            withContext(Dispatchers.Main) { design.showNoProfile() }
        } else {
            uuid = active.uuid
            profileName = active.name
            loadInto(design)
        }

        while (isActive) {
            select<Unit> {
                design.requests.onReceive { req ->
                    val id = uuid ?: return@onReceive
                    when (req) {
                        is TunnelsSettingsDesign.Request.MasterToggle ->
                            launch { onMasterToggle(design, id, req.on) }
                        TunnelsSettingsDesign.Request.Save ->
                            launch { onSave(design, id) }
                        TunnelsSettingsDesign.Request.AddEntry ->
                            withContext(Dispatchers.Main) { design.addEntry() }
                    }
                }
            }
        }
    }

    private suspend fun loadInto(design: TunnelsSettingsDesign) {
        val id = uuid ?: return
        val managed = withProfile { isTunnelsManaged(id) }
        val configJson = withProfile { queryTunnelsConfigJson(id) }
        val config = configJson
            ?.let { runCatching { json.decodeFromString(TunnelsConfig.serializer(), it) }.getOrNull() }
            ?: TunnelsConfig()
        val proxyOptions = loadProxyOptions(id)
        withContext(Dispatchers.Main) {
            design.bind(profileName, managed, config, proxyOptions)
        }
    }

    private suspend fun loadProxyOptions(id: UUID): List<String> {
        val yaml = withProfile { readImportedConfigYaml(id) }.orEmpty()
        if (yaml.isBlank()) return emptyList()
        val snapshot = runCatching { Clash.parseProfileSnapshotFromYaml(yaml) }.getOrNull()
            ?: return emptyList()
        return proxyOptionsFromSnapshot(snapshot)
    }

    private fun proxyOptionsFromSnapshot(snapshot: ProfileSnapshot): List<String> {
        val proxies = snapshot.proxies.mapNotNull { obj ->
            (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        }
        val groups = ProxyGroupsYamlPreview.listProxyGroupNames(snapshot)
        return (proxies + groups).distinct().sorted()
    }

    private suspend fun onMasterToggle(design: TunnelsSettingsDesign, id: UUID, on: Boolean) {
        if (on) {
            withContext(Dispatchers.Main) {
                design.setContentEnabled(true)
                design.showStatus(null, false)
            }
            return
        }
        if (withProfile { isTunnelsManaged(id) }) {
            val emptyJson = json.encodeToString(TunnelsConfig.serializer(), TunnelsConfig())
            val previewJson = withProfile { previewSetTunnels(id, emptyJson) }
            applyPreview(previewJson)
            withProfile { setTunnelsManaged(id, false) }
        }
        loadInto(design)
        withContext(Dispatchers.Main) {
            design.setContentEnabled(false)
            design.showStatus(getString(R.string.tunnels_disabled), false)
        }
    }

    private suspend fun onSave(design: TunnelsSettingsDesign, id: UUID) {
        val model = withContext(Dispatchers.Main) { design.readModel() }

        for ((index, entry) in model.entries.withIndex()) {
            val err = TunnelsValidator.validate(entry)
            if (err != null) {
                withContext(Dispatchers.Main) {
                    design.showStatus(validationMessage(index, err), true)
                }
                return
            }
        }

        withContext(Dispatchers.Main) { design.setSaveBusy(true) }
        try {
            val cfgJson = json.encodeToString(TunnelsConfig.serializer(), model)
            val previewJson = withProfile { previewSetTunnels(id, cfgJson) }
            val preview = previewJson
                ?.let { runCatching { json.decodeFromString(YamlPreview.serializer(), it) }.getOrNull() }
            if (preview == null || !preview.valid) {
                withContext(Dispatchers.Main) {
                    design.showStatus(
                        preview?.error?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.tunnels_err_invalid),
                        true,
                    )
                }
                return
            }
            val ok = withProfile { applyYamlPreview(preview.id) }
            if (ok) {
                withProfile { setTunnelsManaged(id, true) }
                withContext(Dispatchers.Main) {
                    design.showStatus(getString(R.string.tunnels_saved), false)
                }
            } else {
                withContext(Dispatchers.Main) {
                    design.showStatus(getString(R.string.tunnels_err_invalid), true)
                }
            }
        } finally {
            withContext(Dispatchers.Main) { design.setSaveBusy(false) }
        }
    }

    private fun validationMessage(index: Int, err: TunnelsValidator.Error): String {
        val row = getString(R.string.tunnels_entry_title_fmt, index + 1)
        val detail = when (err) {
            TunnelsValidator.Error.ADDRESS_INVALID -> getString(R.string.tunnels_err_address)
            TunnelsValidator.Error.TARGET_EMPTY -> getString(R.string.tunnels_err_target)
            TunnelsValidator.Error.PROXY_EMPTY -> getString(R.string.tunnels_err_proxy)
            TunnelsValidator.Error.NETWORK_INVALID -> getString(R.string.tunnels_err_network)
        }
        return "$row: $detail"
    }

    private suspend fun applyPreview(previewJson: String?): Boolean {
        val preview = previewJson
            ?.let { runCatching { json.decodeFromString(YamlPreview.serializer(), it) }.getOrNull() }
            ?: return false
        if (!preview.valid) return false
        return withProfile { applyYamlPreview(preview.id) }
    }
}
