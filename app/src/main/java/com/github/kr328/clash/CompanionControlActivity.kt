package com.github.kr328.clash

import android.widget.EditText
import com.github.kr328.clash.companion.CompanionStore
import com.github.kr328.clash.companion.controller.CompanionClient
import com.github.kr328.clash.companion.controller.CompanionDiscovery
import com.github.kr328.clash.companion.controller.CompanionError
import com.github.kr328.clash.companion.controller.ControllerStore
import com.github.kr328.clash.companion.ui.CompanionControlDesign
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.withProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

class CompanionControlActivity : BaseActivity<CompanionControlDesign>() {
    private val store by lazy { ControllerStore(this) }
    private var discovery: CompanionDiscovery? = null

    override suspend fun main() {
        val design = CompanionControlDesign(this, store.list(), ::onAgentClick)
        setContentDesign(design)

        // Reconnect-by-deviceId: keep stored agents' addresses fresh as they re-announce (§4.4).
        discovery = CompanionDiscovery(this) { discovered ->
            discovered.forEach { d ->
                if (store.get(d.deviceId) != null) {
                    Log.i("Companion controller: discovery refreshed ${d.name} -> ${d.host}:${d.port}")
                    store.updateAddress(d.deviceId, d.host, d.port)
                }
            }
        }.also { it.start() }

        awaitCancellation()
    }

    override fun onDestroy() {
        discovery?.stop()
        super.onDestroy()
    }

    private fun onAgentClick(deviceId: String) {
        val agent = store.get(deviceId) ?: return
        val actions = arrayOf(
            getString(R.string.companion_action_power),
            getString(R.string.companion_action_share),
            getString(R.string.companion_action_rename),
            getString(R.string.companion_action_unpair),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(agent.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> togglePower(agent)
                    1 -> shareSubscription(agent)
                    2 -> renameAgent(agent)
                    3 -> confirmUnpair(agent)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun client(agent: ControllerStore.PairedAgent): CompanionClient {
        val self = CompanionStore(this)
        return CompanionClient(agent, self.deviceId, self.displayName)
    }

    private fun togglePower(agent: ControllerStore.PairedAgent) = launch(Dispatchers.IO) {
        try {
            val resulting = client(agent).power("toggle")
            design?.showToast(getString(R.string.companion_power_now, resulting), ToastDuration.Short)
        } catch (e: CompanionError) {
            design?.showToast(e.message ?: getString(R.string.companion_request_failed), ToastDuration.Long)
        } catch (e: Exception) {
            Log.w("Companion controller: power https://${agent.host}:${agent.port} failed: ${e.javaClass.simpleName}: ${e.message}")
            design?.showToast(R.string.companion_unreachable, ToastDuration.Long)
        }
    }

    private fun shareSubscription(agent: ControllerStore.PairedAgent) = launch(Dispatchers.IO) {
        val active = withProfile { queryActive() }
        if (active == null || active.type != Profile.Type.Url || active.source.isBlank()) {
            design?.showToast(R.string.companion_no_shareable, ToastDuration.Long)
            return@launch
        }
        try {
            client(agent).subscription(active.source, active.name)
            design?.showToast(R.string.companion_shared, ToastDuration.Short)
        } catch (e: CompanionError) {
            design?.showToast(e.message ?: getString(R.string.companion_request_failed), ToastDuration.Long)
        } catch (e: Exception) {
            Log.w("Companion controller: subscription https://${agent.host}:${agent.port} failed: ${e.javaClass.simpleName}: ${e.message}")
            design?.showToast(R.string.companion_unreachable, ToastDuration.Long)
        }
    }

    private fun renameAgent(agent: ControllerStore.PairedAgent) {
        val input = EditText(this).apply {
            setText(agent.name)
            val pad = (resources.displayMetrics.density * 20).toInt()
            setPadding(pad, pad, pad, pad)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.companion_action_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                launch(Dispatchers.IO) {
                    try {
                        client(agent).rename(name)
                        store.put(agent.copy(name = name))
                        design?.showToast(R.string.companion_renamed, ToastDuration.Short)
                        recreate()
                    } catch (e: CompanionError) {
                        design?.showToast(e.message ?: getString(R.string.companion_request_failed), ToastDuration.Long)
                    } catch (e: Exception) {
                        design?.showToast(R.string.companion_unreachable, ToastDuration.Long)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmUnpair(agent: ControllerStore.PairedAgent) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.companion_action_unpair)
            .setMessage(getString(R.string.companion_unpair_message, agent.name))
            .setPositiveButton(R.string.companion_action_unpair) { _, _ ->
                store.remove(agent.deviceId)
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
