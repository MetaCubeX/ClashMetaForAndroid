package com.github.kr328.clash.design.component

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.github.kr328.clash.common.compat.getDrawableCompat

class ProxyView(
    context: Context,
    config: ProxyViewConfig,
) : View(context) {

    init {
        background = context.getDrawableCompat(config.clickableBackground)
    }

    var state: ProxyViewState? = null
        set(value) {
            field = value
            delayTestPending = false
            delayOverrideText = null
            lastObservedDelay = value?.proxy?.delay ?: Int.MIN_VALUE
            delayHandler.removeCallbacks(delayTimeoutRunnable)
        }
    var onDelayClick: (() -> Unit)? = null

    private val delayRect = RectF()

    private val delayTouchRect = RectF()

    private var delayPressed = false

    private var delayScale = 1f
    private var delayAnimProgress = 0f
    private var delayTestPending = false
    private var delayOverrideText: String? = null
    private var lastObservedDelay: Int = Int.MIN_VALUE

    private val delayHandler = Handler(Looper.getMainLooper())
    private val delayTimeoutRunnable = Runnable {
        if (delayTestPending) {
            delayTestPending = false
            delayOverrideText = "--"
            postInvalidate()
        }
    }

    constructor(context: Context) : this(context, ProxyViewConfig(context, 2))

    private fun dp(v: Float): Float {
        return v * resources.displayMetrics.density
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state == null) {
            return super.onTouchEvent(event)
        }

        if (!delayTouchRect.isEmpty) {
            val inside = delayTouchRect.contains(event.x, event.y)

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    if (inside) {
                        delayPressed = true
                        invalidate()
                        return true
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (delayPressed && !inside) {
                        delayPressed = false
                        invalidate()
                    }

                    if (delayPressed) {
                        return true
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (delayPressed) {
                        delayPressed = false
                        invalidate()

                        if (inside) {
                            startDelayTest()
                            onDelayClick?.invoke()
                        }

                        return true
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (delayPressed) {
                        delayPressed = false
                        invalidate()
                        return true
                    }
                }
            }
        }

        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val state = state ?: return super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED ->
                resources.displayMetrics.widthPixels

            MeasureSpec.AT_MOST, MeasureSpec.EXACTLY ->
                MeasureSpec.getSize(widthMeasureSpec)

            else -> throw IllegalArgumentException("invalid measure spec")
        }

        state.paint.apply {
            reset()
            textSize = state.config.textSize
            getTextBounds("Stub!", 0, 1, state.rect)
        }

        val textHeight = state.rect.height()

        val expectHeight = (
                state.config.layoutPadding * 2 +
                        state.config.contentPadding * 2 +
                        textHeight * 2 +
                        state.config.textMargin
                ).toInt()

        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED ->
                expectHeight

            MeasureSpec.AT_MOST, MeasureSpec.EXACTLY ->
                expectHeight.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))

            else -> throw IllegalArgumentException("invalid measure spec")
        }

        setMeasuredDimension(width, height)
    }

    override fun draw(canvas: Canvas) {
        val state = state ?: return super.draw(canvas)

        if (state.update(false))
            postInvalidate()

        val currentDelay = state.proxy.delay
        if (currentDelay != lastObservedDelay) {
            lastObservedDelay = currentDelay

            if (currentDelay in 1..Short.MAX_VALUE) {
                if (delayTestPending || delayOverrideText != null) {
                    delayTestPending = false
                    delayOverrideText = null
                    delayHandler.removeCallbacks(delayTimeoutRunnable)
                    postInvalidate()
                }
            }
        }

        if (delayPressed && delayAnimProgress < 1f) {
            delayAnimProgress += 0.15f
            postInvalidate()
        }

        if (!delayPressed && delayAnimProgress > 0f) {
            delayAnimProgress -= 0.15f
            postInvalidate()
        }

        delayScale = 1f - delayAnimProgress * 0.1f

        val width = width.toFloat()
        val height = height.toFloat()

        val paint = state.paint
        paint.reset()

        paint.color = state.background
        paint.style = Paint.Style.FILL

        canvas.apply {

            if (state.config.proxyLine == 1) {

                drawRect(0f, 0f, width, height, paint)

            } else {

                val path = state.path
                path.reset()

                path.addRoundRect(
                    state.config.layoutPadding,
                    state.config.layoutPadding,
                    width - state.config.layoutPadding,
                    height - state.config.layoutPadding,
                    state.config.cardRadius,
                    state.config.cardRadius,
                    Path.Direction.CW
                )

                paint.setShadowLayer(
                    state.config.cardRadius,
                    state.config.cardOffset,
                    state.config.cardOffset,
                    state.config.shadow
                )

                drawPath(path, paint)

            }
        }

        super.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val state = state ?: return

        val paint = state.paint
        val width = width.toFloat()
        val height = height.toFloat()

        paint.reset()
        paint.textSize = state.config.textSize
        paint.isAntiAlias = true

        val delayPadding = state.config.delayPadding

        val delayStub = "9999"
        paint.getTextBounds(delayStub, 0, delayStub.length, state.rect)

        val fixedDelayTextWidth = state.rect.width().toFloat()
        val delayAreaWidth = fixedDelayTextWidth + delayPadding * 2

        val delayText = delayOverrideText ?: state.delayText

        val delayCount = paint.breakText(
            delayText,
            false,
            (delayAreaWidth - delayPadding * 2).coerceAtLeast(0f),
            null
        )

        paint.getTextBounds(delayText, 0, delayCount, state.rect)
        val delayTextWidth = state.rect.width().toFloat()

        val mainTextWidth = (
                width -
                        state.config.layoutPadding * 2 -
                        state.config.contentPadding * 2
                ).coerceAtLeast(0f)

        // measure title text bounds
        val titleCount = paint.breakText(
            state.title,
            false,
            mainTextWidth,
            null
        )

        // measure subtitle text bounds
        val subtitleCount = paint.breakText(
            state.subtitle,
            false,
            mainTextWidth,
            null
        )

        // text draw measure
        val textOffset = (paint.descent() + paint.ascent()) / 2

        val fm = paint.fontMetrics
        val delayAreaHeight = (fm.descent - fm.ascent) + delayPadding * 2

        val delayAreaLeft =
            width - state.config.layoutPadding - delayAreaWidth - dp(5f)

        val delayAreaTop =
            height / 2f - delayAreaHeight / 2f

        val delayAreaRight =
            delayAreaLeft + delayAreaWidth

        val delayAreaBottom =
            delayAreaTop + delayAreaHeight

        delayRect.set(delayAreaLeft, delayAreaTop, delayAreaRight, delayAreaBottom)

        val extra = dp(24f)

        delayTouchRect.set(
            delayRect.left - extra,
            delayRect.top - extra,
            delayRect.right + extra,
            delayRect.bottom + extra
        )

        val alpha = (0x24 + delayAnimProgress * 60).toInt()

        paint.color = Color.argb(
            alpha,
            Color.red(state.controls),
            Color.green(state.controls),
            Color.blue(state.controls)
        )

        paint.style = Paint.Style.FILL

        canvas.save()

        canvas.scale(
            delayScale,
            delayScale,
            delayRect.centerX(),
            delayRect.centerY()
        )

        canvas.drawRoundRect(
            delayRect,
            delayAreaHeight / 2f,
            delayAreaHeight / 2f,
            paint
        )

        paint.color = state.controls

        val x = delayRect.centerX() - delayTextWidth / 2f
        val y = delayRect.centerY() - (fm.ascent + fm.descent) / 2f

        canvas.drawText(delayText, 0, delayCount, x, y, paint)

        canvas.restore()

        val fadeWidth = dp(16f)
        val fadeStart = (delayRect.left - fadeWidth).coerceAtLeast(0f)
        val fadeEnd = delayRect.left.coerceAtLeast(fadeStart + 1f)

        val baseColor = state.controls
        val fadeShader = LinearGradient(
            fadeStart,
            0f,
            fadeEnd,
            0f,
            intArrayOf(
                baseColor,
                Color.argb(
                    0,
                    Color.red(baseColor),
                    Color.green(baseColor),
                    Color.blue(baseColor)
                )
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        paint.shader = fadeShader

        canvas.drawText(
            state.title,
            0,
            titleCount,
            state.config.layoutPadding + state.config.contentPadding,
            state.config.layoutPadding +
                    (height - state.config.layoutPadding * 2) / 3f - textOffset,
            paint
        )

        canvas.drawText(
            state.subtitle,
            0,
            subtitleCount,
            state.config.layoutPadding + state.config.contentPadding,
            state.config.layoutPadding +
                    (height - state.config.layoutPadding * 2) / 3f * 2 - textOffset,
            paint
        )

        paint.shader = null
    }

    private fun startDelayTest() {
        delayTestPending = true
        delayOverrideText = "···"
        delayHandler.removeCallbacks(delayTimeoutRunnable)
        delayHandler.postDelayed(delayTimeoutRunnable, 5000L)
        invalidate()
    }
}
