package com.github.kr328.clash

import android.widget.ImageView
import com.github.kr328.clash.companion.CompanionPin
import com.github.kr328.clash.companion.CompanionStore
import com.github.kr328.clash.companion.QrCode
import com.github.kr328.clash.companion.agent.CompanionAgent
import com.github.kr328.clash.companion.agent.CompanionGatewayService
import com.github.kr328.clash.companion.agent.PairingStore
import com.github.kr328.clash.companion.controller.CompanionClient
import com.github.kr328.clash.companion.controller.CompanionError
import com.github.kr328.clash.companion.controller.ControllerStore
import com.github.kr328.clash.companion.protocol.PairingPayload
import com.github.kr328.clash.companion.ui.CompanionDesign
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.withProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.QRResult.QRSuccess
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class CompanionActivity : BaseActivity<CompanionDesign>() {
    private val store by lazy { CompanionStore(this) }
    private val pairingStore by lazy { PairingStore(this) }

    private val scanLauncher = registerForActivityResult(ScanQRCode(), ::onScanResult)

    override suspend fun main() {
        val design = CompanionDesign(this, store, ::onAgentToggle)
        setContentDesign(design)

        // The toggle persists ON across restarts, but the service doesn't auto-survive a process
        // restart — reconcile on open so "Show QR/PIN" actually has a running gateway.
        if (store.agentEnabled && !CompanionAgent.isReady()) {
            CompanionGatewayService.start(this)
        }

        while (isActive) {
            val request = design.requests.receive()
            when (request) {
                CompanionDesign.Request.ShowQr -> showQrDialog()
                CompanionDesign.Request.ShowPin -> showPinDialog()
                CompanionDesign.Request.ManagePairings -> showPairingsDialog()
                CompanionDesign.Request.ScanToPair -> scanLauncher.launch(null)
                CompanionDesign.Request.OpenControl ->
                    startActivity(CompanionControlActivity::class.intent)
            }
        }
    }

    /**
     * Returns true when the gateway is up and pairing payloads can be produced. If the toggle is
     * on but the service isn't running yet (e.g. just opened the screen), starts it and asks the
     * user to retry; if the toggle is off, prompts to enable.
     */
    private fun ensureAgentReady(): Boolean {
        if (CompanionAgent.isReady()) return true
        if (store.agentEnabled) {
            CompanionGatewayService.start(this)
            launch { design?.showToast(R.string.companion_starting, ToastDuration.Long) }
        } else {
            launch { design?.showToast(R.string.companion_enable_first, ToastDuration.Long) }
        }
        return false
    }

    private fun onAgentToggle(enabled: Boolean) {
        if (enabled) {
            CompanionGatewayService.start(this)
        } else {
            CompanionGatewayService.stop(this)
        }
    }

    private fun showQrDialog() {
        if (!ensureAgentReady()) return
        val ip = CompanionAgent.lanAddress()
        if (ip == null) {
            launch { design?.showToast(R.string.companion_no_lan, ToastDuration.Long) }
            return
        }

        val port = CompanionAgent.port
        val payload = PairingPayload(
            ip = ip,
            port = port,
            id = store.deviceId,
            fp = CompanionAgent.fingerprint!!,
            token = pairingStore.issueToken(),
            name = store.displayName,
            app = CompanionStore.APP_ID,
        ).toUri()

        val image = ImageView(this).apply {
            val size = (resources.displayMetrics.density * 280).toInt()
            setImageBitmap(QrCode.encode(payload, size))
            val pad = (resources.displayMetrics.density * 24).toInt()
            setPadding(pad, pad, pad, pad)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.companion_show_qr_title)
            .setMessage(getString(R.string.companion_qr_dialog_message) + "\n\n$ip:$port")
            .setView(image)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** PIN pairing for camera-less controllers (e.g. SlothClash on a PC). */
    private fun showPinDialog() {
        if (!ensureAgentReady()) return
        val pin = CompanionPin.generate()
        val ip = CompanionAgent.lanAddress()
        val density = resources.displayMetrics.density
        val pad = (density * 24).toInt()

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        container.addView(
            android.widget.TextView(this).apply {
                text = getString(R.string.companion_pin_dialog_message)
                textSize = 14f
                alpha = 0.7f
                gravity = android.view.Gravity.CENTER
            },
        )
        // The code itself: large, bold, accent-coloured, spaced — readable across the room on a TV.
        container.addView(
            android.widget.TextView(this).apply {
                text = pin
                textSize = 40f
                setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                letterSpacing = 0.4f // space the digits without spaces (keeps them on one line)
                maxLines = 1
                gravity = android.view.Gravity.CENTER
                setTextColor(resolveThemedColor(com.google.android.material.R.attr.colorPrimary))
                setPadding(0, (density * 16).toInt(), 0, (density * 12).toInt())
            },
        )
        if (ip != null) {
            container.addView(
                android.widget.TextView(this).apply {
                    text = "$ip:${CompanionAgent.port}"
                    textSize = 13f
                    alpha = 0.6f
                    gravity = android.view.Gravity.CENTER
                },
            )
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.companion_show_pin_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showPairingsDialog() {
        val pairings = pairingStore.list()
        if (pairings.isEmpty()) {
            launch { design?.showToast(R.string.companion_no_pairings, ToastDuration.Long) }
            return
        }
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val labels = pairings.map { p ->
            (p.label ?: getString(R.string.companion_pairing_unnamed)) + " · " + df.format(Date(p.pairedAt))
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.companion_manage_pairings_title)
            .setItems(labels) { _, which ->
                confirmRevoke(pairings[which].tokenHash, labels[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRevoke(tokenHash: String, label: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.companion_revoke_title)
            .setMessage(getString(R.string.companion_revoke_message, label))
            .setPositiveButton(R.string.companion_revoke_confirm) { _, _ ->
                pairingStore.revoke(tokenHash)
                launch { design?.showToast(R.string.companion_revoked, ToastDuration.Short) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onScanResult(result: QRResult) {
        if (result !is QRSuccess) return
        val raw = result.content.rawValue ?: return
        val payload = PairingPayload.parse(raw)
        if (payload == null) {
            launch { design?.showToast(R.string.companion_pair_invalid, ToastDuration.Long) }
            return
        }
        val agent = ControllerStore.PairedAgent(
            deviceId = payload.id,
            name = payload.name ?: payload.ip,
            app = payload.app ?: "",
            fp = payload.fp,
            token = payload.token,
            host = payload.ip,
            port = payload.port,
        )
        ControllerStore(this).put(agent)
        // One-scan flow: right after pairing, offer to send the current subscription to the
        // just-paired device, so "scan QR -> sub is on the TV" is a single gesture.
        launch {
            val active = withProfile { queryActive() }
            val shareable = active?.takeIf { it.type == Profile.Type.Url && it.source.isNotBlank() }
            promptAfterPair(agent, shareable)
        }
    }

    private fun promptAfterPair(agent: ControllerStore.PairedAgent, shareable: Profile?) {
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.companion_pair_success, agent.name))
        if (shareable != null) {
            builder
                .setMessage(getString(R.string.companion_share_after_pair, shareable.name))
                .setPositiveButton(R.string.companion_action_share) { _, _ ->
                    shareSubscriptionTo(agent, shareable)
                }
                .setNegativeButton(R.string.companion_not_now, null)
        } else {
            builder
                .setMessage(R.string.companion_no_shareable)
                .setPositiveButton(android.R.string.ok, null)
        }
        builder.show()
    }

    private fun shareSubscriptionTo(agent: ControllerStore.PairedAgent, profile: Profile) =
        launch(Dispatchers.IO) {
            try {
                CompanionClient(agent, store.deviceId, store.displayName)
                    .subscription(profile.source, profile.name)
                design?.showToast(R.string.companion_shared, ToastDuration.Short)
            } catch (e: CompanionError) {
                design?.showToast(e.message ?: getString(R.string.companion_request_failed), ToastDuration.Long)
            } catch (e: Exception) {
                design?.showToast(R.string.companion_unreachable, ToastDuration.Long)
            }
        }
}
