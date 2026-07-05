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
 * Saturation × Value square for a fixed [hue]: saturation runs left→right (0..1), value runs
 * top→bottom (1..0). A draggable dot marks the current pick. Part of the custom accent color picker;
 * emits [onChanged] with the new (saturation, value). The parent supplies [hue] from the hue bar.
 */
class SaturationValueView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var hue: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 360f)
            rebuildSaturationShader()
            invalidate()
        }

    var saturation: Float = 1f
        private set
    var value: Float = 1f
        private set

    /** (saturation, value) both in 0..1, called on drag. */
    var onChanged: ((saturation: Float, value: Float) -> Unit)? = null

    private val saturationPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val ringShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x66000000
    }

    private val radiusPx = context.resources.displayMetrics.density * 12f
    private val ringWidthPx = context.resources.displayMetrics.density * 3f
    private val dotRadiusPx = context.resources.displayMetrics.density * 9f

    init {
        ringPaint.strokeWidth = ringWidthPx
        ringShadowPaint.strokeWidth = ringWidthPx + context.resources.displayMetrics.density
    }

    fun setColor(hueDeg: Float, sat: Float, value: Float) {
        this.saturation = sat.coerceIn(0f, 1f)
        this.value = value.coerceIn(0f, 1f)
        hue = hueDeg
    }

    private fun rebuildSaturationShader() {
        if (width == 0) return
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        saturationPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), 0f, Color.WHITE, hueColor, Shader.TileMode.CLAMP,
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildSaturationShader()
        // Value overlay: transparent at the top, opaque black at the bottom.
        valuePaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, radiusPx, radiusPx, saturationPaint)
        canvas.drawRoundRect(0f, 0f, w, h, radiusPx, radiusPx, valuePaint)
        val cx = (saturation * w).coerceIn(dotRadiusPx, w - dotRadiusPx)
        val cy = ((1f - value) * h).coerceIn(dotRadiusPx, h - dotRadiusPx)
        canvas.drawCircle(cx, cy, dotRadiusPx, ringShadowPaint)
        canvas.drawCircle(cx, cy, dotRadiusPx, ringPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
        }
        saturation = (event.x / width).coerceIn(0f, 1f)
        value = (1f - event.y / height).coerceIn(0f, 1f)
        onChanged?.invoke(saturation, value)
        invalidate()
        return true
    }
}
