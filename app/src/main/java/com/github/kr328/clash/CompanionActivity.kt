package com.github.kr328.clash

import android.widget.ImageView
import com.github.kr328.clash.companion.CompanionStore
import com.github.kr328.clash.companion.QrCode
import com.github.kr328.clash.companion.agent.CompanionAgent
import com.github.kr328.clash.companion.agent.CompanionGatewayService
import com.github.kr328.clash.companion.agent.PairingStore
import com.github.kr328.clash.companion.controller.ControllerStore
import com.github.kr328.clash.companion.protocol.PairingPayload
import com.github.kr328.clash.companion.ui.CompanionDesign
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.QRResult.QRSuccess
import io.github.g00fy2.quickie.ScanQRCode
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

        while (isActive) {
            val request = design.requests.receive()
            when (request) {
                CompanionDesign.Request.ShowQr -> showQrDialog()
                CompanionDesign.Request.ManagePairings -> showPairingsDialog()
                CompanionDesign.Request.ScanToPair -> scanLauncher.launch(null)
                CompanionDesign.Request.OpenControl ->
                    startActivity(CompanionControlActivity::class.intent)
            }
        }
    }

    private fun onAgentToggle(enabled: Boolean) {
        if (enabled) {
            CompanionGatewayService.start(this)
        } else {
            CompanionGatewayService.stop(this)
        }
    }

    private fun showQrDialog() {
        if (!CompanionAgent.isReady()) {
            launch { design?.showToast(R.string.companion_enable_first, ToastDuration.Long) }
            return
        }
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
            .setMessage(getString(R.string.companion_qr_dialog_message) + "\n\n$ip:$port\n\n$payload")
            .setView(image)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.companion_copy_link) { _, _ ->
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("clashctl-pair", payload))
            }
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
        launch {
            design?.showToast(
                getString(R.string.companion_pair_success, agent.name),
                ToastDuration.Long,
            )
        }
    }
}
