package com.github.kr328.clash.util

import android.os.DeadObjectException
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.service.remote.IProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Bounded retry on [DeadObjectException] with exponential backoff (50ms..1s, ~10 attempts).
 * Prevents 100% CPU spin if the remote service is permanently dead.
 */
private const val REMOTE_MAX_RETRIES = 10
private const val REMOTE_BACKOFF_BASE_MS = 50L
private const val REMOTE_BACKOFF_MAX_MS = 1000L

suspend fun <T> withClash(
    context: CoroutineContext = Dispatchers.IO,
    block: suspend IClashManager.() -> T
): T {
    var attempt = 0
    while (true) {
        val remote = Remote.service.remote.get()
        val client = remote.clash()

        try {
            return withContext(context) { client.block() }
        } catch (e: DeadObjectException) {
            attempt += 1
            Log.w("Remote services panic (clash, attempt $attempt)")
            Remote.service.remote.reset(remote)
            val backoff = if (attempt <= REMOTE_MAX_RETRIES) {
                (REMOTE_BACKOFF_BASE_MS shl (attempt - 1)).coerceAtMost(REMOTE_BACKOFF_MAX_MS)
            } else {
                REMOTE_BACKOFF_MAX_MS
            }
            delay(backoff)
        }
    }
}

suspend fun <T> withProfile(
    context: CoroutineContext = Dispatchers.IO,
    block: suspend IProfileManager.() -> T
): T {
    var attempt = 0
    while (true) {
        val remote = Remote.service.remote.get()
        val client = remote.profile()

        try {
            return withContext(context) { client.block() }
        } catch (e: DeadObjectException) {
            attempt += 1
            Log.w("Remote services panic (profile, attempt $attempt)")
            Remote.service.remote.reset(remote)
            val backoff = if (attempt <= REMOTE_MAX_RETRIES) {
                (REMOTE_BACKOFF_BASE_MS shl (attempt - 1)).coerceAtMost(REMOTE_BACKOFF_MAX_MS)
            } else {
                REMOTE_BACKOFF_MAX_MS
            }
            delay(backoff)
        }
    }
}
