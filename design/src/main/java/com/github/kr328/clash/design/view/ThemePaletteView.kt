package com.github.kr328.clash.design.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.annotation.ColorInt
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.R

class ThemePaletteView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()

    @ColorInt
    var colors: IntArray = intArrayOf(0xFF2FA36B.toInt(), 0xFFB6F6D3.toInt(), 0xFFF8FAFD.toInt())
        set(value) {
            field = value
            invalidate()
        }

    var checked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = width.coerceAtMost(height).toFloat()
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        bounds.set(left, top, left + size, top + size)

        for (i in 0 until 4) {
            paint.color = colors.getOrElse(i) { colors.last() }
            canvas.drawArc(bounds, -90f + i * 90f, 90f, true, paint)
        }

        if (checked) {
            val inset = context.getPixels(R.dimen.theme_palette_check_inset).toFloat()
            val checkBounds = RectF(bounds).apply { inset(inset, inset) }
            paint.color = colors.getOrElse(1) { 0xFFFFFFFF.toInt() }
            canvas.drawCircle(checkBounds.centerX(), checkBounds.centerY(), checkBounds.width() / 2f, paint)
            paint.color = colors.first()
            paint.strokeWidth = context.getPixels(R.dimen.theme_palette_check_stroke).toFloat()
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            val cx = checkBounds.centerX()
            val cy = checkBounds.centerY()
            canvas.drawLine(cx - size * 0.10f, cy, cx - size * 0.02f, cy + size * 0.08f, paint)
            canvas.drawLine(cx - size * 0.02f, cy + size * 0.08f, cx + size * 0.13f, cy - size * 0.10f, paint)
            paint.style = Paint.Style.FILL
        }
    }
}
