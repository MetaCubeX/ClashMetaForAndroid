package com.github.kr328.clash.util

import android.content.Context
import com.github.kr328.clash.common.util.SubscriptionNameGuesser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Async name resolver for subscription imports.
 *
 * Old behaviour was synchronous: the UI froze for up to 8 seconds while we
 * tried to read Profile-Title before even creating the profile or starting
 * `commit()`. On slow networks that meant blocked UI **plus** frequent fallback
 * to a host-based name (the timeout fired before Profile-Title arrived).
 *
 * This helper starts the network guess in the background, returns a fast
 * synchronous host-based name for the placeholder, and exposes a [Deferred]
 * the caller awaits **after** `commit()` to upgrade the profile name to the
 * real Profile-Title — without blocking the user-visible progress bar.
 *
 * The background guess uses a 20s timeout: generous enough for Cloudflare-
 * fronted panels on mobile, but bounded so we don't hold the rename forever
 * if the panel never responds.
 */
object AsyncNameResolver {
    /**
     * @property preliminaryName host-based name available immediately; safe
     *   to pass to `create(Profile.Type.Url, name, url)` without any wait.
     * @property betterName resolves to a Profile-Title-derived name when the
     *   background fetch finishes within 20s **and** it differs from
     *   [preliminaryName]. Otherwise resolves to `null` — caller should
     *   leave the profile name as-is.
     */
    data class Pending(
        val preliminaryName: String,
        val betterName: Deferred<String?>,
    )

    fun start(
        scope: CoroutineScope,
        context: Context,
        url: String,
    ): Pending {
        val preliminary = SubscriptionNameGuesser.guessFast(url)
        val better = scope.async {
            withTimeoutOrNull(20_000L) {
                runCatching {
                    SubscriptionNameGuesser.guess(context, url)
                }.getOrNull()
            }?.takeIf { it.isNotBlank() && it != preliminary }
        }
        return Pending(preliminary, better)
    }
}
