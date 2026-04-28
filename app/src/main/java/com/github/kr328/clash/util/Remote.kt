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

class RemoteServiceUnavailableException(cause: Throwable) :
    RuntimeException("Remote service unavailable after retries", cause)

suspend fun <T> withClash(
    context: CoroutineContext = Dispatchers.IO,
    block: suspend IClashManager.() -> T
): T {
    var lastError: DeadObjectException? = null
    repeat(REMOTE_MAX_RETRIES) { attempt ->
        val remote = Remote.service.remote.get()
        val client = remote.clash()

        try {
            return withContext(context) { client.block() }
        } catch (e: DeadObjectException) {
            lastError = e
            Log.w("Remote services panic (clash, attempt ${attempt + 1}/$REMOTE_MAX_RETRIES)")
            Remote.service.remote.reset(remote)
            val backoff = (REMOTE_BACKOFF_BASE_MS shl attempt).coerceAtMost(REMOTE_BACKOFF_MAX_MS)
            delay(backoff)
        }
    }
    throw RemoteServiceUnavailableException(lastError ?: DeadObjectException())
}

suspend fun <T> withProfile(
    context: CoroutineContext = Dispatchers.IO,
    block: suspend IProfileManager.() -> T
): T {
    var lastError: DeadObjectException? = null
    repeat(REMOTE_MAX_RETRIES) { attempt ->
        val remote = Remote.service.remote.get()
        val client = remote.profile()

        try {
            return withContext(context) { client.block() }
        } catch (e: DeadObjectException) {
            lastError = e
            Log.w("Remote services panic (profile, attempt ${attempt + 1}/$REMOTE_MAX_RETRIES)")
            Remote.service.remote.reset(remote)
            val backoff = (REMOTE_BACKOFF_BASE_MS shl attempt).coerceAtMost(REMOTE_BACKOFF_MAX_MS)
            delay(backoff)
        }
    }
    throw RemoteServiceUnavailableException(lastError ?: DeadObjectException())
}
