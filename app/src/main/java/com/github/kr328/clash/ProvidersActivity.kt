package com.github.kr328.clash

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.ProvidersDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.theme.YumeTheme
import com.github.kr328.clash.remote.Broadcasts
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ProvidersActivity : ComponentActivity(), Broadcasts.Observer {
    private lateinit var design: ProvidersDesign

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        design = ProvidersDesign(this)
        setContent {
            YumeTheme {
                design.Content()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                design.patchProviders(queryProviders())
                collectRequests()
            }
        }
    }

    private fun collectRequests() = lifecycleScope.launch {
        repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            design.requests.consumeEach { req ->
                when (req) {
                    is ProvidersDesign.Request.UpdateAll -> {
                        try {
                            updateAll(design.providers)
                        } finally {
                            // 无论成功或失败，都必须复位全局与行内刷新状态并刷新数据，避免界面卡住
                            design.finishUpdateAll()
                            design.patchProviders(queryProviders())
                        }
                    }

                    is ProvidersDesign.Request.Update -> {
                        try {
                            runCatching {
                                updateProviderWithRetry(req.provider.type, req.provider.name)
                            }.onFailure { e ->
                                Toast.makeText(
                                    this@ProvidersActivity,
                                    getString(
                                        R.string.format_update_provider_failure,
                                        req.provider.name,
                                        e.message
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } finally {
                            // 清除该行的更新中状态，并刷新数据
                            design.markProviderUpdating(req.provider.name, false)
                            design.patchProviders(queryProviders())
                        }
                    }
                }
            }
        }
    }

    private suspend fun queryProviders(): List<Provider> = withContext(Dispatchers.IO) {
        withClash { queryProviders().sorted() }
    }

    private suspend fun updateAll(providers: List<Provider>) = coroutineScope {
        val updatable = providers.filter { it.vehicleType != Provider.VehicleType.Inline }
        if (updatable.isEmpty()) return@coroutineScope
        // 有限并发，避免过载/触发风控；可以按需调高/调低
        val parallelism = 4
        val semaphore = Semaphore(parallelism)
        val jobs = updatable.map { p ->
            async {
                semaphore.withPermit {
                    try {
                        updateProviderWithRetry(p.type, p.name)
                    } catch (e: Throwable) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@ProvidersActivity,
                                getString(R.string.format_update_provider_failure, p.name, e.message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } finally {
                        // 清理该行转圈，并增量刷新以更新“更新时间”
                        design.markProviderUpdating(p.name, false)
                        design.patchProviders(queryProviders())
                    }
                }
            }
        }
        // 等待全部完成
        jobs.awaitAll()
    }

    private suspend fun updateProviderWithRetry(type: Provider.Type, name: String) {
        var attempt = 0
        val maxRetries = 2
        var lastError: Throwable? = null
        while (attempt <= maxRetries) {
            try {
                withClash { updateProvider(type, name) }
                return
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                lastError = e
                if (attempt == maxRetries) break
                // 指数退避：0.5s, 1.5s
                val backoff = if (attempt == 0) 500L else 1500L
                delay(backoff)
            }
            attempt++
        }
        throw lastError ?: IllegalStateException("unknown error")
    }

    override fun onStart() {
        super.onStart()
        Remote.broadcasts.addObserver(this)
    }

    override fun onStop() {
        super.onStop()
        Remote.broadcasts.removeObserver(this)
    }

    override fun onServiceRecreated() { /* no-op */
    }

    override fun onStarted() { /* no-op */
    }

    override fun onStopped(cause: String?) { /* no-op */
    }

    override fun onProfileChanged() { /* no-op */
    }

    override fun onProfileUpdateCompleted(uuid: java.util.UUID?) { /* no-op */
    }

    override fun onProfileUpdateFailed(uuid: java.util.UUID?, reason: String?) { /* no-op */
    }

    override fun onProfileLoaded() { /* no-op */
    }
}