package com.github.kr328.clash

import android.os.Bundle
import androidx.core.view.WindowCompat
import com.github.kr328.clash.design.DnsHostsSettingsDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.service.model.YamlPreview
import com.github.kr328.clash.service.util.DnsHostsConfig
import com.github.kr328.clash.service.util.DnsHostsValidator
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Per-profile DNS & Hosts editor over the active profile. Reads the profile's
 * parsed `dns:`/`hosts:` (Path B snapshot), writes them back via the engine-
 * validated preview pipeline, and toggles the per-profile "managed" marker that
 * drives subscription-refresh preservation. Master toggle OFF tears the blocks
 * down cleanly.
 */
class DnsHostsSettingsActivity : BaseActivity<DnsHostsSettingsDesign>() {
    private val json = Json { ignoreUnknownKeys = true }

    private var uuid: UUID? = null
    private var profileName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override suspend fun main() {
        val design = DnsHostsSettingsDesign(this)
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
                        is DnsHostsSettingsDesign.Request.MasterToggle ->
                            launch { onMasterToggle(design, id, req.on) }
                        DnsHostsSettingsDesign.Request.Save ->
                            launch { onSave(design, id) }
                    }
                }
            }
        }
    }

    private suspend fun loadInto(design: DnsHostsSettingsDesign) {
        val id = uuid ?: return
        val managed = withProfile { isDnsHostsManaged(id) }
        val configJson = withProfile { queryDnsHostsConfigJson(id) }
        val config = configJson
            ?.let { runCatching { json.decodeFromString(DnsHostsConfig.serializer(), it) }.getOrNull() }
            ?: DnsHostsConfig()
        withContext(Dispatchers.Main) { design.bind(profileName, managed, config) }
    }

    private suspend fun onMasterToggle(design: DnsHostsSettingsDesign, id: UUID, on: Boolean) {
        if (on) {
            withContext(Dispatchers.Main) {
                design.setContentEnabled(true)
                design.showStatus(null, false)
            }
            return
        }
        // OFF: clean teardown only if the profile was actually managed (saved before).
        if (withProfile { isDnsHostsManaged(id) }) {
            val emptyJson = json.encodeToString(DnsHostsConfig.serializer(), DnsHostsConfig())
            val previewJson = withProfile { previewSetDnsHosts(id, emptyJson) }
            applyPreview(previewJson)
            withProfile { setDnsHostsManaged(id, false) }
        }
        loadInto(design)
        withContext(Dispatchers.Main) {
            design.setContentEnabled(false)
            design.showStatus(getString(R.string.dns_hosts_disabled), false)
        }
    }

    private suspend fun onSave(design: DnsHostsSettingsDesign, id: UUID) {
        val model = withContext(Dispatchers.Main) { design.readModel() }

        if (DnsHostsValidator.listenError(model.listen) != null) {
            withContext(Dispatchers.Main) {
                design.showStatus(getString(R.string.dns_hosts_err_listen), true)
            }
            return
        }

        withContext(Dispatchers.Main) { design.setSaveBusy(true) }
        try {
            val cfgJson = json.encodeToString(DnsHostsConfig.serializer(), model)
            val previewJson = withProfile { previewSetDnsHosts(id, cfgJson) }
            val preview = previewJson
                ?.let { runCatching { json.decodeFromString(YamlPreview.serializer(), it) }.getOrNull() }
            if (preview == null || !preview.valid) {
                withContext(Dispatchers.Main) {
                    design.showStatus(
                        preview?.error?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.dns_hosts_err_invalid),
                        true,
                    )
                }
                return
            }
            val ok = withProfile { applyYamlPreview(preview.id) }
            if (ok) {
                withProfile { setDnsHostsManaged(id, true) }
                withContext(Dispatchers.Main) {
                    design.showStatus(getString(R.string.dns_hosts_saved), false)
                }
            } else {
                withContext(Dispatchers.Main) {
                    design.showStatus(getString(R.string.dns_hosts_err_invalid), true)
                }
            }
        } finally {
            withContext(Dispatchers.Main) { design.setSaveBusy(false) }
        }
    }

    private suspend fun applyPreview(previewJson: String?): Boolean {
        val preview = previewJson
            ?.let { runCatching { json.decodeFromString(YamlPreview.serializer(), it) }.getOrNull() }
            ?: return false
        if (!preview.valid) return false
        return withProfile { applyYamlPreview(preview.id) }
    }
}
