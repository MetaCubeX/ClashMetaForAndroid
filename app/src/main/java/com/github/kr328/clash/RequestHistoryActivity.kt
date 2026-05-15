package com.github.kr328.clash

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.design.RequestHistoryDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.RequestHistorySnapshot
import com.github.kr328.clash.service.model.formatRequestHistoryExport
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter

class RequestHistoryActivity : BaseActivity<RequestHistoryDesign>() {
    override suspend fun main() {
        val design = RequestHistoryDesign(this)
        setContentDesign(design)

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        suspend fun querySnapshot(): RequestHistorySnapshot? {
            val raw = withClash { queryRequestHistory() }
            return withContext(Dispatchers.Default) {
                runCatching {
                    json.decodeFromString(RequestHistorySnapshot.serializer(), raw)
                }.getOrElse {
                    Log.w("Request history decode failed; raw size=${raw.length}", it)
                    null
                }
            }
        }

        suspend fun refresh() {
            querySnapshot()?.let { design.patchSnapshot(it) }
        }

        val refreshJob = launch {
            while (isActive) {
                if (activityStarted) {
                    runCatching { refresh() }.onFailure {
                        Log.w("Request history refresh failed", it)
                    }
                }
                delay(2000)
            }
        }

        runCatching { withClash { startRequestHistoryTracking() } }

        try {
            refresh()

            while (isActive) {
                select<Unit> {
                    design.requests.onReceive {
                        when (it) {
                            RequestHistoryDesign.Request.Clear -> launch {
                                withClash { clearRequestHistory() }
                                refresh()
                                design.showToast(R.string.request_history_cleared, ToastDuration.Short)
                            }
                            RequestHistoryDesign.Request.Export -> launch {
                                val snapshot = querySnapshot() ?: return@launch
                                val output = startActivityForResult(
                                    ActivityResultContracts.CreateDocument("text/csv"),
                                    "clashfest-request-history.csv",
                                )
                                if (output != null) {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            writeExport(snapshot, output)
                                        }
                                    }.onSuccess {
                                        design.showToast(R.string.file_exported, ToastDuration.Long)
                                    }.onFailure { e ->
                                        design.showExceptionToast(e as? Exception ?: Exception(e))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            refreshJob.cancel()
            runCatching { withClash { stopRequestHistoryTracking() } }
        }
    }

    private fun writeExport(snapshot: RequestHistorySnapshot, uri: Uri) {
        OutputStreamWriter(contentResolver.openOutputStream(uri)).use {
            it.write(formatRequestHistoryExport(snapshot.requests))
        }
    }
}
