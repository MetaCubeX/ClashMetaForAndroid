package com.github.kr328.clash.agent

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * Marks a run of inline code. Carries no background of its own — [MarkdownTextView]
 * paints one behind it — because a plain background span is drawn as a hard,
 * full-line-height rectangle, which stripes the paragraph and collides with the
 * lines above and below.
 */
class InlineCodeSpan(private val monospace: Boolean) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) = apply(tp)

    override fun updateMeasureState(tp: TextPaint) = apply(tp)

    private fun apply(tp: TextPaint) {
        // Monospace only for ASCII. The mono font has no CJK glyphs, so forcing
        // it on a Chinese node name falls back to another family and drags the
        // whole line's metrics with it.
        if (monospace) tp.typeface = Typeface.MONOSPACE
    }
}

/**
 * TextView that paints rounded highlights behind [InlineCodeSpan] runs.
 *
 * The box hugs the glyphs instead of filling the line box, so inline code reads
 * as a chip inside the sentence rather than a grey band across it. Runs that
 * wrap are drawn one rect per line.
 */
class MarkdownTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightRect = RectF()
    private val cornerRadius: Float

    /** Transparent disables the highlight entirely. */
    var inlineCodeColor: Int = Color.TRANSPARENT
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    init {
        cornerRadius = 4f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        drawInlineCodeHighlights(canvas)
        super.onDraw(canvas)
    }

    private fun drawInlineCodeHighlights(canvas: Canvas) {
        if (inlineCodeColor == Color.TRANSPARENT) return

        val spanned = text as? Spanned ?: return
        val textLayout = layout ?: return
        val spans = spanned.getSpans(0, spanned.length, InlineCodeSpan::class.java)
        if (spans.isEmpty()) return

        highlightPaint.color = inlineCodeColor

        val offsetX = totalPaddingLeft.toFloat()
        val offsetY = totalPaddingTop.toFloat()

        // Sized off the text size rather than the font's ascent/descent. CJK
        // fonts report ~1.45em of metrics, so a box built from those plus any
        // padding would be as tall as the line pitch and the chips on adjacent
        // lines would touch. These ratios sit inside the glyphs' own extent.
        val aboveBaseline = textSize * 0.88f
        val belowBaseline = textSize * 0.24f

        for (span in spans) {
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            if (start < 0 || end <= start) continue

            val firstLine = textLayout.getLineForOffset(start)
            val lastLine = textLayout.getLineForOffset(end - 1)

            for (line in firstLine..lastLine) {
                val from = maxOf(start, textLayout.getLineStart(line))
                // Visible end excludes the trailing newline, so the box does not
                // run out to the right margin on a wrapped run.
                val to = minOf(end, textLayout.getLineVisibleEnd(line))
                if (to <= from) continue

                val baseline = textLayout.getLineBaseline(line).toFloat() + offsetY
                highlightRect.set(
                    textLayout.getPrimaryHorizontal(from) + offsetX,
                    baseline - aboveBaseline,
                    textLayout.getPrimaryHorizontal(to) + offsetX,
                    baseline + belowBaseline,
                )
                canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, highlightPaint)
            }
        }
    }
}
