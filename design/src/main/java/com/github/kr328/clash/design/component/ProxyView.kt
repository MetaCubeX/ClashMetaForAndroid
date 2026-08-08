package com.github.kr328.clash.design.component

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.github.kr328.clash.common.compat.getDrawableCompat
import com.github.kr328.clash.design.store.UiStore

class ProxyView(
    context: Context,
    config: ProxyViewConfig,
) : View(context) {

    init {
        background = context.getDrawableCompat(config.clickableBackground)
    }

    var state: ProxyViewState? = null

    /** Reused across frames: onDraw runs on every list fling. */
    private val badgeRect = RectF()

    constructor(context: Context) : this(context, ProxyViewConfig(context, 2))
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val state = state ?: return super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED ->
                resources.displayMetrics.widthPixels
            MeasureSpec.AT_MOST, MeasureSpec.EXACTLY ->
                MeasureSpec.getSize(widthMeasureSpec)
            else ->
                throw IllegalArgumentException("invalid measure spec")
        }

        state.paint.apply {
            reset()

            textSize = state.config.textSize

            getTextBounds("Stub!", 0, 1, state.rect)
        }

        val textHeight = state.rect.height()
        val exceptHeight = (state.config.layoutPadding * 2 +
                state.config.contentPadding * 2 +
                textHeight * 2 +
                state.config.textMargin).toInt()

        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED ->
                exceptHeight
            MeasureSpec.AT_MOST, MeasureSpec.EXACTLY ->
                exceptHeight.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
            else ->
                throw IllegalArgumentException("invalid measure spec")
        }

        setMeasuredDimension(width, height)
    }

    override fun draw(canvas: Canvas) {
        val state = state ?: return super.draw(canvas)

        if (state.update(false))
            postInvalidate()

        val width = width.toFloat()
        val height = height.toFloat()

        val paint = state.paint

        paint.reset()

        paint.color = state.background
        paint.style = Paint.Style.FILL

        // draw background
        canvas.apply {
            if (state.config.proxyLine==1) {
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
                    Path.Direction.CW,
                )

                paint.setShadowLayer(
                    state.config.cardRadius,
                    state.config.cardOffset,
                    state.config.cardOffset,
                    state.config.shadow
                )

                drawPath(path, paint)

                clipPath(path)
            }
        }

        super.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val state = state ?: return

        val paint = state.paint
        val config = state.config

        val width = width.toFloat()
        val height = height.toFloat()

        val left = config.layoutPadding + config.contentPadding
        val right = width - config.layoutPadding - config.contentPadding
        if (right <= left) return

        applyTextPaint(paint, state)
        val textOffset = (paint.descent() + paint.ascent()) / 2

        // delay: right aligned, vertically centred over both rows
        val delayCount = paint.breakText(state.delayText, false, right - left, null)
        paint.getTextBounds(state.delayText, 0, delayCount, state.rect)
        val delayWidth = state.rect.width().toFloat()
        canvas.drawText(
            state.delayText,
            0,
            delayCount,
            right - delayWidth,
            height / 2f - textOffset,
            paint,
        )

        val contentRight =
            if (delayWidth > 0f) right - delayWidth - config.textMargin * 2 else right

        val innerHeight = height - config.layoutPadding * 2
        val titleBaseline = config.layoutPadding + innerHeight / 3f - textOffset
        val subtitleBaseline = config.layoutPadding + innerHeight / 3f * 2 - textOffset

        // title owns the full content width; badges sit on the subtitle row
        val titleCount =
            paint.breakText(state.title, false, (contentRight - left).coerceAtLeast(0f), null)
        canvas.drawText(state.title, 0, titleCount, left, titleBaseline, paint)

        // subtitle row: [type] [chain] remaining subtitle text
        var cursor = left
        val badgeCenterY = subtitleBaseline + textOffset

        if (state.typeLabel.isNotEmpty()) {
            cursor = drawBadge(canvas, state, state.typeLabel, cursor, badgeCenterY, contentRight, false)
        }
        if (state.chainLabel.isNotEmpty()) {
            cursor = drawBadge(canvas, state, state.chainLabel, cursor, badgeCenterY, contentRight, true)
        }

        if (state.subtitle.isNotEmpty() && contentRight > cursor) {
            // Badges leave their own size and colour on the shared paint, so the
            // text paint must be re-applied before the subtitle run.
            applyTextPaint(paint, state)
            val subtitleCount =
                paint.breakText(state.subtitle, false, contentRight - cursor, null)
            canvas.drawText(state.subtitle, 0, subtitleCount, cursor, subtitleBaseline, paint)
        }
    }

    private fun applyTextPaint(paint: Paint, state: ProxyViewState) {
        paint.reset()
        paint.isAntiAlias = true
        paint.textSize = state.config.textSize
        paint.color = state.controls
    }

    /**
     * Draws one badge and returns the x to continue the row at. When the badge
     * would not fit inside [maxRight] it is skipped entirely rather than drawn
     * overlapping the delay text, so narrow grid cells degrade cleanly.
     */
    private fun drawBadge(
        canvas: Canvas,
        state: ProxyViewState,
        label: String,
        x: Float,
        centerY: Float,
        maxRight: Float,
        accent: Boolean,
    ): Float {
        val config = state.config
        val paint = state.paint

        paint.reset()
        paint.isAntiAlias = true
        paint.textSize = config.badgeTextSize

        val badgeWidth = paint.measureText(label) + config.badgePaddingHorizontal * 2
        if (x + badgeWidth > maxRight) return x

        val half = config.badgeHeight / 2f
        badgeRect.set(x, centerY - half, x + badgeWidth, centerY + half)

        paint.style = Paint.Style.FILL
        paint.color = when {
            accent && state.isSelected -> config.accentBadgeSelectedBackground
            accent -> config.accentBadgeBackground
            state.isSelected -> config.badgeSelectedBackground
            else -> config.badgeBackground
        }
        canvas.drawRoundRect(badgeRect, config.badgeRadius, config.badgeRadius, paint)

        paint.color = when {
            accent && state.isSelected -> config.accentBadgeSelectedText
            accent -> config.accentBadgeText
            state.isSelected -> config.badgeSelectedText
            else -> config.badgeText
        }
        canvas.drawText(
            label,
            x + config.badgePaddingHorizontal,
            badgeRect.centerY() - (paint.descent() + paint.ascent()) / 2f,
            paint,
        )

        return badgeRect.right + config.badgeGap
    }
}
