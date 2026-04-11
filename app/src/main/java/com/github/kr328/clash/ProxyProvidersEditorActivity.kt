package com.github.kr328.clash

import android.os.Bundle
import androidx.core.view.WindowCompat
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.ProxyProvidersEditorDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.SubscriptionPick
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.ProxyProviderUiRow
import com.github.kr328.clash.service.util.ProxyProvidersUi
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.util.UUID

class ProxyProvidersEditorActivity : BaseActivity<ProxyProvidersEditorDesign>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let toolbar apply status-bar padding via WindowInsets; do not use system “fits” padding on root.
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override suspend fun main() {
        val uuid: UUID = intent.uuid ?: return finish()
        val design = ProxyProvidersEditorDesign(this)
        setContentDesign(design)

        val picks = withProfile { subscriptionPicksForMerge(uuid) }
        design.setSubscriptionPicks(picks)

        val yaml = withProfile { readProxyProvidersYaml(uuid) }
        val labelsJson = withProfile { readProxyProviderLabelsJson(uuid) }
        val labels = ProxyProvidersUi.parseLabelsJson(labelsJson)
        val parsed = ProxyProvidersUi.parseRows(yaml, labels)
        if (parsed.isEmpty()) {
            design.setRows(
                listOf(
                    ProxyProviderUiRow(
                        title = "",
                        url = "https://",
                        intervalSeconds = 3600L,
                    ),
                ),
            )
        } else {
            design.setRows(parsed)
        }

        while (isActive) {
            select<Unit> {
                design.requests.onReceive { req ->
                    when (req) {
                        ProxyProvidersEditorDesign.Request.Save ->
                            launch { save(uuid, design) }
                        ProxyProvidersEditorDesign.Request.UpdateAllProviders ->
                            launch { updateAll(design) }
                        ProxyProvidersEditorDesign.Request.OpenMergedGroupConstructor ->
                            startActivity(
                                ProxyProviderMergedGroupActivity::class.intent.setUUID(uuid),
                            )
                    }
                }
            }
        }
    }

    private suspend fun subscriptionPicksForMerge(currentUuid: UUID): List<SubscriptionPick> {
        return withProfile {
            queryAll()
                .filter { p ->
                    p.imported &&
                        p.type != Profile.Type.File &&
                        p.source.isNotBlank() &&
                        p.uuid != currentUuid
                }
                .map { p ->
                    val intervalSec = when {
                        p.interval <= 0L -> 3600L
                        else -> (p.interval / 1000L).coerceAtLeast(60L)
                    }
                    SubscriptionPick(
                        label = p.name,
                        rowTitle = p.name,
                        url = p.source.trim(),
                        intervalSeconds = intervalSec,
                    )
                }
        }
    }

    private suspend fun save(uuid: UUID, design: ProxyProvidersEditorDesign) {
        val raw = design.getRows()
        for (row in raw) {
            val u = row.url.trim()
            if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) {
                design.showToast(R.string.proxy_providers_invalid_url, ToastDuration.Long)
                return
            }
        }
        val valid = raw.filter { it.url.trim().isNotEmpty() }
        try {
            val doc = ProxyProvidersUi.buildProxyProvidersDocument(valid)
            val labels = ProxyProvidersUi.buildLabels(valid)
            val labelsJson = ProxyProvidersUi.labelsToJson(labels)
            val ok = withProfile { replaceProxyProvidersYaml(uuid, doc) }
            if (!ok) {
                design.showToast(R.string.proxy_providers_toast_failed, ToastDuration.Long)
                return
            }
            withProfile { writeProxyProviderLabelsJson(uuid, labelsJson) }
            design.showToast(R.string.proxy_providers_toast_saved, ToastDuration.Long)
            finish()
        } catch (e: Exception) {
            design.showExceptionToast(e)
        }
    }

    private suspend fun updateAll(design: ProxyProvidersEditorDesign) {
        if (!clashRunning) {
            design.showToast(R.string.proxy_providers_need_vpn, ToastDuration.Long)
            return
        }
        try {
            withClash {
                val proxies = queryProviders().filter { it.type == Provider.Type.Proxy }
                for (p in proxies) {
                    updateProvider(p.type, p.name)
                }
            }
            design.showToast(R.string.proxy_providers_update_all_ok, ToastDuration.Long)
        } catch (e: Exception) {
            design.showExceptionToast(e)
        }
    }
}
