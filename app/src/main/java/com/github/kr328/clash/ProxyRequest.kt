package com.github.kr328.clash

import kotlinx.coroutines.CancellationException

/**
 * Ends one proxy-page request on failure.
 * [CancellationException] keeps propagating so the caller can leave [ProxyShell.run].
 */
internal suspend fun confineProxyRequest(
    request: suspend () -> Unit,
    onFailure: suspend (Exception) -> Unit,
) {
    try {
        request()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
    }
}
