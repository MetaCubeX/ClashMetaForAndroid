package com.github.kr328.clash

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.lifecycleScope
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setFileName
import com.github.kr328.clash.design.LogsDesign
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LogsActivity : BaseActivity<LogsDesign>() {
    private lateinit var serviceStore: ServiceStore
    private var logsDesign: LogsDesign? = null
    private var logcatServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            logcatServiceBound = true
            logsDesign?.isLogcatRunning = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            logcatServiceBound = false
            logsDesign?.isLogcatRunning = false
        }
    }

    override suspend fun main() {
        serviceStore = ServiceStore(this)

        val currentDesign = LogsDesign(this)
        logsDesign = currentDesign
        setContentDesign(currentDesign)


        updateLogcatStatus()

        for (request in currentDesign.requests) {
            when (request) {
                LogsDesign.Request.OpenLogcat -> {
                    startActivity(LogcatActivity::class.intent)
                    finish()
                }

                LogsDesign.Request.DeleteAll -> {

                }

                LogsDesign.Request.StartLogcat -> {
                    startLogcatService()
                }

                LogsDesign.Request.StopLogcat -> {
                    stopLogcatService()
                }

                is LogsDesign.Request.OpenLogFile -> {
                    startActivity(LogcatActivity::class.intent.setFileName(request.file.fileName))
                }
            }
        }
    }

    private fun updateLogcatStatus() {
        lifecycleScope.launch {

            delay(100)
            try {

                val isRunning = try {

                    logcatServiceBound
                } catch (e: Exception) {
                    false
                }
                logsDesign?.isLogcatRunning = isRunning
            } catch (e: Exception) {
                logsDesign?.isLogcatRunning = false
            }
        }
    }

    private fun startLogcatService() {
        try {
            val intent = Intent(this, LogcatService::class.java)
            startForegroundService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopLogcatService() {
        try {
            if (logcatServiceBound) {
                unbindService(serviceConnection)
                logcatServiceBound = false
            }
            stopService(Intent(this, LogcatService::class.java))
            logsDesign?.isLogcatRunning = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStart() {
        super.onStart()

        updateLogcatStatus()
    }

    override fun onResume() {
        super.onResume()

        updateLogcatStatus()
        logsDesign?.loadLogs()
    }

    override fun onStop() {
        super.onStop()
        
    }

    override fun onDestroy() {
        if (logcatServiceBound) {
            unbindService(serviceConnection)
        }
        super.onDestroy()
    }
}
