package com.github.kr328.clash

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.util.startClashService

class VpnRequestActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startClashService()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        if (savedInstanceState != null) return
        val prepare = readPrepare()
        if (prepare == null) {
            finish()
            return
        }
        launcher.launch(prepare)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun readPrepare(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PREPARE, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PREPARE)
        }
    }

    companion object {
        const val EXTRA_PREPARE = "vpn_prepare"

        fun consentIntent(context: Context, prepare: Intent): Intent {
            return Intent(context, VpnRequestActivity::class.java)
                .putExtra(EXTRA_PREPARE, prepare)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
