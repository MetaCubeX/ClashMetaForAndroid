package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable
import com.github.kr328.clash.design.ui.ToastConfiguration
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.MiuixToastHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

abstract class Design<R>(val context: Context) : CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    @Composable
    abstract fun Content()

    val requests: Channel<R> = Channel(Channel.UNLIMITED)

    private val miuixToastHelper = MiuixToastHelper(context)

    suspend fun showToast(
        resId: Int,
        duration: ToastDuration,
        configure: ToastConfiguration.() -> Unit = {}
    ) {
        return showToast(context.getString(resId), duration, configure)
    }

    suspend fun showToast(
        message: CharSequence,
        duration: ToastDuration,
        configure: ToastConfiguration.() -> Unit = {}
    ) {
        miuixToastHelper.showToast(message, duration, configure)
    }

    protected suspend fun updateStateOnMain(block: () -> Unit) {
        withContext(Dispatchers.Main) {
            block()
        }
    }

    protected fun runOnMain(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(block)
        }
    }

    open fun onDestroy() {
        job.cancel()
        requests.close()
    }
}