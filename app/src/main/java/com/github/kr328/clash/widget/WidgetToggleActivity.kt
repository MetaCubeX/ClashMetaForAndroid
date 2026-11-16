package com.github.kr328.clash.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService

/**
 * A transparent, no-UI activity used by the widget. Using an activity instead of a
 * broadcast lets us request VPN permission on first use, and it is allowed to be
 * launched from a widget PendingIntent.
 */
@Suppress("DEPRECATION")
class WidgetToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        overridePendingTransition(0, 0)

        if (StatusClient(this).currentProfile() != null) {
            stopClashService()
            finish()
            return
        }

        val vpnRequest = startClashService()

        if (vpnRequest == null) {
            finish()
            return
        }

        try {
            startActivityForResult(vpnRequest, REQUEST_VPN)
        } catch (e: Exception) {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_VPN && resultCode == RESULT_OK) {
            startClashService()
        }

        finish()
    }

    override fun finish() {
        super.finish()

        overridePendingTransition(0, 0)
    }

    companion object {
        private const val REQUEST_VPN = 1
    }
}
