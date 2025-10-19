package com.github.kr328.clash

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.fileName
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.LogcatDesign
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.log.LogcatFilter
import com.github.kr328.clash.log.LogcatReader
import com.github.kr328.clash.util.logsDir
import kotlinx.coroutines.*
import java.io.OutputStreamWriter

class LogcatActivity : BaseActivity<LogcatDesign>() {
    private var logcatServiceBound = false
    private var fileHelper: FileHelper? = null
    private var logcatDesign: LogcatDesign? = null
    private var fileName: String? = null
    private var isStreaming = false
    private var logcatService: LogcatService? = null
    private var updateJob: Job? = null

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let { performExport(it) }
        }

    private class FileHelper(val launcher: ActivityResultLauncher<String>)

    private val connection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            logcatServiceBound = false
            logcatService = null
            logcatDesign?.isRunning = false
            updateJob?.cancel()
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            logcatServiceBound = true
            val binder = service as? LogcatService.LocalBinder
            logcatService = binder?.getService()


            startPeriodicUpdate()
        }
    }

    override suspend fun main() {
        fileName = intent?.fileName
        isStreaming = (fileName == null)


        fileHelper = FileHelper(exportLauncher)

        val currentDesign = LogcatDesign(this, isStreaming)
        logcatDesign = currentDesign
        design = currentDesign
        setContentDesign(currentDesign)

        
        if (fileName != null) {
            loadLocalFile()
        } else {
            startStreaming()
        }


        if (isStreaming) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {

                    logcatDesign?.isRunning = false
                    finish()
                }
            })
        }


        for (request in currentDesign.requests) {
            when (request) {
                LogcatDesign.Request.Close -> {

                    logcatDesign?.isRunning = false

                    if (isStreaming) {

                        lifecycleScope.launch {
                            try {
                                stopService(Intent(this@LogcatActivity, LogcatService::class.java))

                                delay(800)
                                finish()
                            } catch (e: Exception) {
                                Log.e("Error stopping logcat service: ${e.message}", e)
                                finish()
                            }
                        }
                    } else {

                        finish()
                    }
                }
                LogcatDesign.Request.Delete -> {
                    if (!isStreaming && fileName != null) {
                        deleteLogFile()
                    }
                }
                LogcatDesign.Request.Export -> {
                    exportLogFile()
                }
            }
        }
    }

    private fun loadLocalFile() {
        lifecycleScope.launch {
            try {
                val file = LogFile.parseFromFileName(fileName!!)
                if (file == null) {
                    Toast.makeText(
                        this@LogcatActivity,
                        getString(com.github.kr328.clash.design.R.string.invalid_log_file),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return@launch
                }

                val messages = LogcatReader(this@LogcatActivity, file).readAll().reversed()
                logcatDesign?.patchMessages(messages, 0, messages.size)
            } catch (e: Exception) {
                Log.e("Fail to read log file $fileName: ${e.message}", e)
                Toast.makeText(
                    this@LogcatActivity,
                    getString(com.github.kr328.clash.design.R.string.invalid_log_file),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun startStreaming() {
        lifecycleScope.launch {
            try {
                startForegroundServiceCompat(LogcatService::class.intent)


                bindService(LogcatService::class.intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                Log.e("Failed to start logcat service: ${e.message}", e)
            }
        }
    }

    private fun startPeriodicUpdate() {
        updateJob?.cancel()
        updateJob = lifecycleScope.launch {

            try {
                val initialSnapshot = logcatService?.snapshot(true)
                if (initialSnapshot != null) {
                    logcatDesign?.setInitialMessages(initialSnapshot.messages)
                }
            } catch (e: Exception) {
                Log.e("Failed to load initial logcat: ${e.message}", e)
            }


            while (isActive) {
                try {
                    val snapshot = logcatService?.snapshot(false)
                    if (snapshot != null && (snapshot.removed > 0 || snapshot.appended > 0)) {
                        logcatDesign?.patchMessages(snapshot.messages, snapshot.removed, snapshot.appended)
                    }
                } catch (e: Exception) {
                    Log.e("Failed to update logcat: ${e.message}", e)
                }


                delay(500)
            }
        }
    }

    private fun deleteLogFile() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val logFile = LogFile.parseFromFileName(fileName!!)
                    if (logFile != null) {
                        logsDir.resolve(logFile.fileName).delete()
                    }
                }
                finish()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@LogcatActivity, "删除失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportLogFile() {
        val logFile = fileName?.let { LogFile.parseFromFileName(it) }
        if (logFile != null) {
            fileHelper?.launcher?.launch(logFile.fileName)
        } else {
            Toast.makeText(this@LogcatActivity, "无法识别的日志文件", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performExport(uri: Uri) {
        lifecycleScope.launch {
            try {
                val currentMessages = logcatDesign?.messages ?: emptyList()
                LogcatFilter(OutputStreamWriter(contentResolver.openOutputStream(uri)), this@LogcatActivity).use {
                    withContext(Dispatchers.IO) {
                        val logFile = fileName?.let { LogFile.parseFromFileName(it) }
                        if (logFile != null) {
                            it.writeHeader(logFile.date)
                        }
                        currentMessages.forEach { msg -> it.writeMessage(msg) }
                    }
                }
                runOnUiThread {
                    Toast.makeText(
                        this@LogcatActivity,
                        getString(com.github.kr328.clash.design.R.string.file_exported),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@LogcatActivity, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()


        updateJob?.cancel()


        if (logcatServiceBound) {
            unbindService(connection)
        }
    }
}