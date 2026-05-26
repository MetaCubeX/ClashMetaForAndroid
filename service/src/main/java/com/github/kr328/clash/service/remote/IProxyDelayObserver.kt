package com.github.kr328.clash.service.remote

import com.github.kr328.kaidl.BinderInterface

/**
 * IPC bridge for per-proxy delay push during a health check. ClashService
 * adapts the JNI [ProxyDelayCallback] into this AIDL observer so the
 * activity-side coroutine can patch one row at a time without polling
 * queryProxyGroup on a timer.
 *
 * [onDelay] fires once per proxy as soon as its URLTest resolves. When
 * [errMsg] is empty the call is a success and [delayMs] carries the
 * measured latency; otherwise the proxy timed out / failed and [delayMs]
 * is meaningless. [onComplete] fires exactly once after the whole group
 * has been measured, with [error] carrying the engine reason if the call
 * couldn't even start (group missing / wrong type).
 */
@BinderInterface
interface IProxyDelayObserver {
    fun onDelay(group: String, proxy: String, delayMs: Int, errMsg: String)
    fun onComplete(error: String?)
}
