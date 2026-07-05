package com.github.kr328.clash.design.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Horizontal rainbow hue bar (0..360°) with a draggable thumb. Part of the custom accent color
 * picker; emits [onChanged] with the new hue so the [SaturationValueView] can re-tint.
 */
class HueBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var hue: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 360f)
            invalidate()
        }

    var onChanged: ((hue: Float) -> Unit)? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x66000000
    }

    private val density = context.resources.displayMetrics.density
    private val cornerPx = density * 8f

    init {
        thumbPaint.strokeWidth = density * 3f
        thumbShadowPaint.strokeWidth = density * 4f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val hues = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
        barPaint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, hues, null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, cornerPx, cornerPx, barPaint)
        val cx = (hue / 360f * w).coerceIn(h / 2f, w - h / 2f)
        val r = h / 2f - density
        canvas.drawCircle(cx, h / 2f, r, thumbShadowPaint)
        canvas.drawCircle(cx, h / 2f, r, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
        }
        hue = (event.x / width * 360f).coerceIn(0f, 360f)
        onChanged?.invoke(hue)
        invalidate()
        return true
    }
}
