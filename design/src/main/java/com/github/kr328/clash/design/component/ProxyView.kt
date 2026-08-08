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

        val width = width.toFloat()
        val height = height.toFloat()

        paint.textSize = state.config.textSize

        // measure delay text bounds
        val delayCount = paint.breakText(
            state.delayText,
            false,
            (width - state.config.layoutPadding * 2 - state.config.contentPadding * 2)
                .coerceAtLeast(0f),
            null
        )

        state.paint.getTextBounds(state.delayText, 0, delayCount, state.rect)

        val delayWidth = state.rect.width()

        var mainTextWidth = (width -
                state.config.layoutPadding * 2 -
                state.config.contentPadding * 2 -
                delayWidth -
                state.config.textMargin * 2
                )
            .coerceAtLeast(0f)

        // measure chain badge before title so the title gets the remaining space
        var badgeWidth = 0f
        var badgeGap = 0f
        if (state.hasChain && state.chainLabel.isNotEmpty()) {
            val badgeTextSize = state.config.textSize * 0.62f
            paint.reset()
            paint.textSize = badgeTextSize
            val labelWidth = paint.measureText(state.chainLabel)
            val badgePaddingX = badgeTextSize * 0.5f
            badgeWidth = labelWidth + badgePaddingX * 2
            badgeGap = state.config.textMargin
            mainTextWidth = (mainTextWidth - badgeWidth - badgeGap).coerceAtLeast(0f)
        }

        // measure title text bounds
        val titleCount = paint.breakText(
            state.title,
            false,
            mainTextWidth,
            null,
        )

        // measure subtitle text bounds
        paint.reset()
        paint.textSize = state.config.textSize
        val subtitleCount = paint.breakText(
            state.subtitle,
            false,
            mainTextWidth,
            null,
        )

        // text draw measure
        val textOffset = (paint.descent() + paint.ascent()) / 2

        paint.reset()

        paint.textSize = state.config.textSize
        paint.isAntiAlias = true
        paint.color = state.controls

        // draw delay
        canvas.apply {
            val x = width - state.config.layoutPadding - state.config.contentPadding - delayWidth
            val y = height / 2f - textOffset

            drawText(state.delayText, 0, delayCount, x, y, paint)
        }

        // draw title
        val titleX = state.config.layoutPadding + state.config.contentPadding
        val titleY = state.config.layoutPadding +
                (height - state.config.layoutPadding * 2) / 3f - textOffset

        canvas.apply {
            drawText(state.title, 0, titleCount, titleX, titleY, paint)
        }

        // draw chain badge right after the title line
        if (state.hasChain && state.chainLabel.isNotEmpty()) {
            val badgeTextSize = state.config.textSize * 0.62f
            val badgeHeight = state.config.textSize * 1.15f

            val titleWidth = paint.measureText(state.title, 0, titleCount)
            val bx = (titleX + titleWidth + state.config.textMargin * 2)
                .coerceAtLeast(titleX)
            val by = state.config.layoutPadding + state.config.textSize * 0.18f

            val badgeRect = RectF(
                bx,
                by,
                bx + badgeWidth,
                by + badgeHeight
            )

            // When selected the card background is already primary: invert the badge.
            val badgeFill = if (state.isSelected) state.config.onPrimary else state.config.primary
            val badgeText = if (state.isSelected) state.config.primary else state.config.onPrimary

            paint.reset()
            paint.isAntiAlias = true
            paint.color = badgeFill
            paint.style = Paint.Style.FILL

            canvas.drawRoundRect(badgeRect, badgeHeight / 2f, badgeHeight / 2f, paint)

            paint.color = badgeText
            paint.textSize = badgeTextSize
            paint.style = Paint.Style.FILL

            val baseline = badgeRect.centerY() - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(
                state.chainLabel,
                badgeRect.left + badgeTextSize * 0.5f,
                baseline,
                paint
            )
        }

        // draw subtitle
        canvas.apply {
            val x = state.config.layoutPadding + state.config.contentPadding
            val y = state.config.layoutPadding +
                    (height - state.config.layoutPadding * 2) / 3f * 2 - textOffset

            drawText(state.subtitle, 0, subtitleCount, x, y, paint)
        }
    }
}
