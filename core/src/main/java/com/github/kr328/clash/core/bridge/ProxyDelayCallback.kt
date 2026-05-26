package com.github.kr328.clash.core.bridge

import androidx.annotation.Keep

/**
 * Per-proxy delay push for an in-flight health check. The native side fires
 * [report] once per proxy as soon as the URLTest for that proxy resolves —
 * UI can patch a single row immediately instead of polling queryProxyGroup.
 * [complete] fires after every proxy in the group has reported (or the call
 * was aborted). `error` is null on success, otherwise carries the engine's
 * reason — e.g. "group not found" or "invalid group type".
 *
 * `delayMs` is 0 when [errMsg] is non-empty (the proxy timed out or failed
 * to connect). Callers should treat (errMsg.isNotEmpty()) as "no signal"
 * rather than "0ms latency".
 */
@Keep
interface ProxyDelayCallback {
    fun report(proxyName: String, delayMs: Int, errMsg: String)
    fun complete(error: String?)
}
