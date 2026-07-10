package com.github.kr328.clash.service.clash.module

internal data class NetworkSwitchReactionDecision<T : Any>(
    val reaction: T? = null,
    val retryAfterMs: Long? = null,
    val cancelPendingRetry: Boolean = false,
)

internal class NetworkSwitchReactionGate<T : Any>(
    private val startedAt: Long,
    private val startupGraceMs: Long = 5_000L,
    private val flapGuardMs: Long = 3_000L,
) {
    private var initialized = false
    private var observed: T? = null
    private var lastReactionAt: Long? = null

    fun observe(candidate: T?, now: Long, enabled: Boolean): NetworkSwitchReactionDecision<T> {
        if (!initialized) {
            initialized = true
            observed = candidate
            return NetworkSwitchReactionDecision(cancelPendingRetry = true)
        }
        if (candidate == observed) return NetworkSwitchReactionDecision()

        observed = candidate
        return decide(candidate, now, enabled)
    }

    fun retry(candidate: T?, now: Long, enabled: Boolean): NetworkSwitchReactionDecision<T> {
        if (candidate != observed) return observe(candidate, now, enabled)

        return decide(candidate, now, enabled)
    }

    private fun decide(
        candidate: T?,
        now: Long,
        enabled: Boolean,
    ): NetworkSwitchReactionDecision<T> {
        if (candidate == null || !enabled || now - startedAt < startupGraceMs) {
            return NetworkSwitchReactionDecision(cancelPendingRetry = true)
        }

        val previousReactionAt = lastReactionAt
        if (previousReactionAt != null) {
            val elapsed = now - previousReactionAt
            if (elapsed < flapGuardMs) {
                return NetworkSwitchReactionDecision(
                    retryAfterMs = flapGuardMs - elapsed,
                    cancelPendingRetry = true,
                )
            }
        }

        lastReactionAt = now
        return NetworkSwitchReactionDecision(
            reaction = candidate,
            cancelPendingRetry = true,
        )
    }
}
