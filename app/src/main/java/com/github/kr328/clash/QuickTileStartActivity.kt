package com.github.kr328.clash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.util.autoSelectFirstRuntimeProxy
import com.github.kr328.clash.util.prepareQuickTileStart
import com.github.kr328.clash.util.startClashService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class QuickTileStartActivity : Activity(), CoroutineScope by MainScope() {
    private var requestInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        if (savedInstanceState == null) {
            startFromForeground()
        }
    }

    private fun startFromForeground() {
        launch {
            runCatching {
                prepareQuickTileStart(includeInstalledPackages = false)
                val vpnRequest = startClashService()
                if (vpnRequest != null) {
                    requestInFlight = true
                    @Suppress("DEPRECATION")
                    startActivityForResult(vpnRequest, REQUEST_VPN)
                    return@launch
                }
                finishStarted()
            }.onFailure {
                Log.e("Quick tile start failed", it)
                finish()
            }
        }
    }

    @Deprecated("Deprecated in Android framework")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_VPN) return
        requestInFlight = false

        if (resultCode != RESULT_OK) {
            Log.w("Quick tile VPN permission denied")
            finish()
            return
        }

        launch {
            runCatching {
                val secondRequest = startClashService()
                if (secondRequest != null) {
                    Log.w("Quick tile VPN permission is still required after approval")
                    finish()
                } else {
                    finishStarted()
                }
            }.onFailure {
                Log.e("Quick tile start after VPN permission failed", it)
                finish()
            }
        }
    }

    private suspend fun finishStarted() {
        runCatching {
            autoSelectFirstRuntimeProxy()
        }.onFailure {
            Log.w("Quick tile server auto-selection failed", it)
        }
        finish()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        if (!requestInFlight) {
            cancel()
        }
        super.onDestroy()
    }

    private companion object {
        private const val REQUEST_VPN = 1
    }
}
