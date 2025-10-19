package com.github.kr328.clash.log

import android.content.Context
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.util.logsDir
import java.io.BufferedWriter
import java.io.FileWriter

class LogcatWriter(context: Context) : AutoCloseable {
    private val file = LogFile.generate()
    private val logFile = context.logsDir.resolve(file.fileName)
    private var writer: BufferedWriter? = null
    private var messageCount = 0
    private var hasWrittenHeader = false

    init {
    }

    override fun close() {
        writer?.close()
        if (messageCount == 0 && logFile.exists()) {
            logFile.delete()
        }
    }

    fun appendMessage(message: LogMessage) {
        if (writer == null) {
            logFile.parentFile?.mkdirs()
            writer = BufferedWriter(FileWriter(logFile))
        }

        writer?.let { w ->
            if (!hasWrittenHeader) {
                hasWrittenHeader = true
            }
            w.appendLine(FORMAT.format(message.time.time, message.level.name, message.message))
            w.flush()
            messageCount++
        }
    }

    fun hasMessages(): Boolean = messageCount > 0

    companion object {
        private const val FORMAT = "%d:%s:%s"
    }
}