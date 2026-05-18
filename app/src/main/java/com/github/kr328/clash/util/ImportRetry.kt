package com.github.kr328.clash.util

import com.github.kr328.clash.common.log.Log
import kotlinx.coroutines.delay
import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Retry policy for subscription commit / fetch on transient network errors.
 *
 * Mobile networks intermittently fail subscription fetches in three ways:
 *  - Android DNS resolver gives up after 3 internal tries and throws
 *    [UnknownHostException] ("No address associated with hostname"). On the
 *    next attempt the DNS cache (router / SIM) is hot and the lookup succeeds.
 *  - Mihomo's rule-provider HTTP/2 streams get reset mid-body → [EOFException]
 *    or [IOException] with "unexpected end of stream".
 *  - First TCP handshake on a cold CDN edge eats the connect timeout →
 *    [SocketTimeoutException]; second attempt reuses the warm pool.
 *
 * One retry after ~1.5s recovers from the vast majority of these without
 * making the user re-tap import. We do **not** retry programmer errors,
 * parse failures, or HTTP 4xx responses — only transient I/O.
 */
object ImportRetry {

    suspend fun <T> withTransientRetry(
        maxAttempts: Int = 2,
        delayMs: Long = 1500L,
        block: suspend () -> T,
    ): T {
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Throwable) {
                if (!isTransient(e)) throw e
                lastError = e
                Log.w(
                    "ImportRetry: transient error on attempt ${attempt + 1}/$maxAttempts " +
                        "— ${e.javaClass.simpleName}: ${e.message}",
                )
                if (attempt < maxAttempts - 1) {
                    // Linear backoff: 1.5s, 3s, 4.5s. Linear (not exponential)
                    // keeps the worst case bounded for the user — we never
                    // want import to silently grind for >10s.
                    delay(delayMs * (attempt + 1))
                }
            }
        }
        throw lastError ?: IllegalStateException("ImportRetry exhausted with no exception captured")
    }

    /**
     * Walks the cause chain — Mihomo wraps the underlying I/O error in higher-
     * level subscription / parser exceptions, so the top-level type alone
     * isn't enough to classify.
     */
    private fun isTransient(e: Throwable): Boolean {
        var cur: Throwable? = e
        while (cur != null) {
            when (cur) {
                is UnknownHostException -> return true
                is EOFException -> return true
                is SocketTimeoutException -> return true
                is IOException -> {
                    val msg = cur.message?.lowercase().orEmpty()
                    if (
                        "eof" in msg ||
                        "unexpected end" in msg ||
                        "connection reset" in msg ||
                        "no address associated" in msg ||
                        "failed to connect" in msg ||
                        "stream reset" in msg ||
                        "broken pipe" in msg
                    ) return true
                }
            }
            cur = cur.cause
        }
        return false
    }
}
