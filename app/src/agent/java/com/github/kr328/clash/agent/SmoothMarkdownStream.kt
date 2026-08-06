package com.github.kr328.clash.agent

import android.view.Choreographer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * Presents a bursty cumulative token stream as stable, frame-paced text.
 *
 * The adaptive pacing algorithm is based on markstream-core's
 * smooth-stream-controller.ts, Copyright (c) 2022 Simon He, MIT licensed.
 * This is a native Android implementation using Choreographer; no Vue or DOM code is used.
 */
class SmoothMarkdownStream(
    private val onFrame: (String) -> Unit,
) : Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private var source = ""
    private var visible = ""
    private var scheduled = false
    private var cancelled = false
    private var finishing = false
    private var startedAtNanos = 0L
    private var lastFrameNanos = 0L
    private var lastCommitNanos = 0L
    private var currentCharsPerSecond = MIN_CHARS_PER_SECOND
    private var characterBudget = 0.0
    private var finishContinuation: Continuation<Unit>? = null

    fun submit(cumulativeText: String) {
        if (cancelled || finishing || cumulativeText == source) return
        source = cumulativeText
        if (!source.startsWith(visible)) {
            visible = source.commonPrefixWith(visible)
            onFrame(visible)
        }
        schedule()
    }

    suspend fun finish(finalText: String) {
        if (cancelled) return
        source = finalText
        if (!source.startsWith(visible)) {
            visible = source.commonPrefixWith(visible)
            onFrame(visible)
        }
        finishing = true
        if (visible == source) return
        suspendCancellableCoroutine { continuation ->
            finishContinuation = continuation
            continuation.invokeOnCancellation {
                if (finishContinuation === continuation) finishContinuation = null
            }
            schedule()
        }
    }

    fun cancel() {
        if (cancelled) return
        cancelled = true
        scheduled = false
        choreographer.removeFrameCallback(this)
        finishContinuation?.resume(Unit)
        finishContinuation = null
    }

    /** The text currently committed to the UI. Safe to call from any thread. */
    fun currentText(): String = visible

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (cancelled) return
        if (startedAtNanos == 0L) {
            startedAtNanos = frameTimeNanos
            lastFrameNanos = frameTimeNanos
        }
        if (frameTimeNanos - startedAtNanos < START_DELAY_NANOS) {
            schedule()
            return
        }

        val pending = source.length - visible.length
        if (pending <= 0) {
            completeIfFinished()
            return
        }

        val elapsedSeconds = ((frameTimeNanos - lastFrameNanos).coerceAtMost(MAX_DELTA_NANOS)) / NANOS_PER_SECOND
        lastFrameNanos = frameTimeNanos
        val targetLatency = if (finishing) FINISH_LATENCY_SECONDS else TARGET_LATENCY_SECONDS
        val latencyCharsPerSecond = pending / targetLatency
        val estimatedLatency = pending / max(currentCharsPerSecond, 1.0)
        val targetCharsPerSecond = if (
            pending >= CATCH_UP_THRESHOLD || estimatedLatency >= CATCH_UP_LATENCY_SECONDS
        ) {
            MAX_CHARS_PER_SECOND
        } else {
            latencyCharsPerSecond.coerceIn(MIN_CHARS_PER_SECOND, MAX_CHARS_PER_SECOND)
        }
        currentCharsPerSecond += (targetCharsPerSecond - currentCharsPerSecond) * SPEED_EASING
        characterBudget += currentCharsPerSecond * elapsedSeconds

        val commitIntervalPassed = frameTimeNanos - lastCommitNanos >= MIN_COMMIT_INTERVAL_NANOS
        val requestedCharacters = min(characterBudget.toInt(), MAX_CHARS_PER_COMMIT)
        if (commitIntervalPassed && requestedCharacters > 0) {
            val end = safeSliceEnd(source, visible.length, requestedCharacters)
            if (end > visible.length) {
                val consumed = end - visible.length
                visible = source.substring(0, end)
                characterBudget = max(0.0, characterBudget - consumed)
                lastCommitNanos = frameTimeNanos
                onFrame(visible)
            }
        }

        if (visible == source) completeIfFinished() else schedule()
    }

    private fun completeIfFinished() {
        if (!finishing || visible != source) return
        finishContinuation?.resume(Unit)
        finishContinuation = null
    }

    private fun schedule() {
        if (scheduled || cancelled || visible == source) {
            completeIfFinished()
            return
        }
        scheduled = true
        choreographer.postFrameCallback(this)
    }

    private fun safeSliceEnd(text: String, start: Int, requestedCharacters: Int): Int {
        var index = start
        var remaining = requestedCharacters
        while (index < text.length && remaining > 0) {
            val codePoint = text.codePointAt(index)
            index += Character.charCount(codePoint)
            remaining--

            while (index < text.length) {
                val next = text.codePointAt(index)
                val type = Character.getType(next)
                val extendsCluster = type == Character.NON_SPACING_MARK.toInt() ||
                    type == Character.COMBINING_SPACING_MARK.toInt() ||
                    type == Character.ENCLOSING_MARK.toInt() ||
                    next in 0xFE00..0xFE0F || next in 0x1F3FB..0x1F3FF
                if (extendsCluster) {
                    index += Character.charCount(next)
                } else if (next == ZERO_WIDTH_JOINER && index + 1 < text.length) {
                    index += Character.charCount(next)
                    val joined = text.codePointAt(index)
                    index += Character.charCount(joined)
                } else {
                    break
                }
            }
        }
        return index
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val START_DELAY_NANOS = 24_000_000L
        const val MAX_DELTA_NANOS = 100_000_000L
        const val MIN_COMMIT_INTERVAL_NANOS = 66_000_000L
        const val MIN_CHARS_PER_SECOND = 80.0
        const val MAX_CHARS_PER_SECOND = 1_400.0
        const val TARGET_LATENCY_SECONDS = 0.42
        const val FINISH_LATENCY_SECONDS = 0.16
        const val CATCH_UP_LATENCY_SECONDS = 0.24
        const val CATCH_UP_THRESHOLD = 560
        const val MAX_CHARS_PER_COMMIT = 120
        const val SPEED_EASING = 0.2
        const val ZERO_WIDTH_JOINER = 0x200D
    }
}
