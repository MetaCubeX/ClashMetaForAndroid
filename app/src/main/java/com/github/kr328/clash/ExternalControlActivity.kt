package com.github.kr328.clash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.*
import com.github.kr328.clash.design.R

class ExternalControlActivity : Activity(), CoroutineScope by MainScope() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        when(intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return finish()
                if (uri.host != "install-config" && uri.host != "installconfig") return finish()
                val url = uri.getQueryParameter("url") ?: return finish()
                val name = uri.getQueryParameter("name")
                    ?: getString(R.string.subscription_default_name)

                // SEC: this deeplink is exported, so any site/app can fire it. Never create
                // a profile from an untrusted external source without explicit consent —
                // show the source URL and only proceed if the user confirms.
                confirmInstallConfig(name, url) {
                    launch {
                        val uuid = withProfile {
                            // DL-1: external deeplinks only create a URL-source profile.
                            // File import must come from the in-app picker (which grants
                            // a content-URI), never from an untrusted deeplink path.
                            create(Profile.Type.Url, name).also {
                                patch(it, name, url, 0)
                            }
                        }
                        startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                        finish()
                    }
                }
                // Don't fall through to the synchronous finish() below — the
                // activity must stay alive until the dialog (and, on confirm, the
                // coroutine) completes; it finishes itself, onDestroy cancels the scope.
                return
            }

            Intents.ACTION_TOGGLE_CLASH -> if (externalControlAllowed()) {
                if (isServiceRunning()) {
                    stopClash()
                } else {
                    startClash()
                }
            }

            Intents.ACTION_START_CLASH -> if (externalControlAllowed()) {
                if (!isServiceRunning()) {
                    startClash()
                } else {
                    Toast.makeText(this, R.string.external_control_started, Toast.LENGTH_LONG).show()
                }
            }

            Intents.ACTION_STOP_CLASH -> if (externalControlAllowed()) {
                if (isServiceRunning()) {
                    stopClash()
                } else {
                    Toast.makeText(this, R.string.external_control_stopped, Toast.LENGTH_LONG).show()
                }
            }
        }
        return finish()
    }

    /**
     * Shows an explicit consent dialog naming the external source before a profile is created.
     * The activity's theme is a translucent platform theme, so wrap the dialog context in a
     * Material theme. Decline / dismiss finishes without creating anything.
     */
    private fun confirmInstallConfig(name: String, url: String, onConfirm: () -> Unit) {
        val themed = ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight)
        MaterialAlertDialogBuilder(themed)
            .setTitle(R.string.deeplink_install_confirm_title)
            .setMessage(getString(R.string.deeplink_install_confirm_message, name, url))
            .setPositiveButton(R.string.deeplink_install_confirm_add) { _, _ -> onConfirm() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun startClash() {
        val vpnRequest = startClashService()
        if (vpnRequest != null) {
            Toast.makeText(this, R.string.unable_to_start_vpn, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.external_control_started, Toast.LENGTH_LONG).show()
    }

    private fun stopClash() {
        stopClashService()
        Toast.makeText(this, R.string.external_control_stopped, Toast.LENGTH_LONG).show()
        // SEC-3: an external stop must never be silent — let the user know the
        // VPN was stopped by something other than their own action.
        notifyExternalStop()
    }

    /**
     * Fresh, authoritative running state via the status ContentProvider —
     * unlike Remote.broadcasts.clashRunning, this is never stale when the
     * activity is (cold-)started by an external intent, so external
     * START/STOP/TOGGLE act on the real VPN state.
     */
    private fun isServiceRunning(): Boolean =
        StatusClient(this).statusSnapshot().serviceRunning

    /**
     * SEC-3 gate. External control actions are honored only when the user has
     * not disabled them. When disabled, give brief feedback and do nothing.
     */
    private fun externalControlAllowed(): Boolean {
        if (ServiceStore(this).allowExternalControl) return true
        Toast.makeText(this, R.string.external_control_disabled, Toast.LENGTH_LONG).show()
        return false
    }

    private fun notifyExternalStop() {
        val manager = NotificationManagerCompat.from(this)
        // Best-effort: on Android 13+ without POST_NOTIFICATIONS this is a no-op;
        // the allow-external-control setting is the guaranteed control.
        if (!manager.areNotificationsEnabled()) return

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(
                EXTERNAL_STOP_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_DEFAULT,
            ).setName(getString(R.string.external_stop_channel)).build()
        )

        val notification = NotificationCompat.Builder(this, EXTERNAL_STOP_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(getString(R.string.external_stop_title))
            .setContentText(getString(R.string.external_stop_text))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        manager.notify(EXTERNAL_STOP_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val EXTERNAL_STOP_CHANNEL_ID = "external_control_channel"
        const val EXTERNAL_STOP_NOTIFICATION_ID = 0x4543 // 'EC'
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Q-1: the install-config branch launches on this MainScope; cancel it
        // so the scope doesn't outlive the (immediately finishing) activity.
        cancel()
    }
}