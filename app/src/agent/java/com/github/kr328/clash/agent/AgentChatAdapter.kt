package com.github.kr328.clash.agent

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.NoCopySpan
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.R
import com.github.kr328.clash.agent.model.AgentConversationMessage
import com.github.kr328.clash.agent.model.AgentMessageRole
import com.google.android.material.card.MaterialCardView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.MarkwonTheme
import org.commonmark.node.CodeBlock
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Chat adapter optimized for a single message that grows while the model streams.
 *
 * Stable IDs and payload updates keep RecyclerView from recreating the bubble. Markdown
 * parsing runs away from the main thread and each holder coalesces pending work, so an old
 * partial response can never replace a newer one.
 */
class AgentChatAdapter(
    private val context: Context,
    val messages: MutableList<AgentConversationMessage>,
    private val onContentHeightChanged: (String) -> Unit = {},
) : RecyclerView.Adapter<AgentChatAdapter.Holder>(), Closeable {
    private val markwon = createMarkwon(context)
    private val markdownExecutor = Executors.newSingleThreadExecutor()
    private val attachedHolders = mutableSetOf<Holder>()

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.agent_message_card)
        val text: TextView = view.findViewById(R.id.agent_message_text)
        var boundMessageId: String? = null
        val bindToken = AtomicLong()
        val sequence = AtomicLong()
        val appliedSequence = AtomicLong()
        val renderScheduled = AtomicBoolean()
        val pendingRender = AtomicReference<RenderRequest?>()
        val renderedText = SpannableStringBuilder()
        var measuredHeight = 0
        var heightReportPending = false

        init {
            text.setSpannableFactory(object : Spannable.Factory() {
                override fun newSpannable(source: CharSequence): Spannable =
                    source as? Spannable ?: SpannableString(source)
            })
            text.setText(renderedText, TextView.BufferType.SPANNABLE)
        }
    }

    data class RenderRequest(
        val bindToken: Long,
        val sequence: Long,
        val messageId: String,
        val markdown: String,
        val streaming: Boolean,
    )

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_agent_message, parent, false)
    )

    override fun getItemCount(): Int = messages.size

    override fun getItemId(position: Int): Long = messages[position].id.toStableLong()

    override fun onBindViewHolder(holder: Holder, position: Int) {
        bindMessage(holder, messages[position], fullBind = true)
    }

    override fun onViewRecycled(holder: Holder) {
        holder.bindToken.incrementAndGet()
        holder.boundMessageId = null
        holder.pendingRender.set(null)
        super.onViewRecycled(holder)
    }

    override fun onViewAttachedToWindow(holder: Holder) {
        attachedHolders += holder
        super.onViewAttachedToWindow(holder)
    }

    override fun onViewDetachedFromWindow(holder: Holder) {
        attachedHolders -= holder
        super.onViewDetachedFromWindow(holder)
    }

    private fun bindMessage(holder: Holder, message: AgentConversationMessage, fullBind: Boolean) {
        val identityChanged = holder.boundMessageId != message.id
        if (identityChanged) {
            holder.boundMessageId = message.id
            holder.bindToken.incrementAndGet()
            holder.appliedSequence.set(0L)
            holder.pendingRender.set(null)
            holder.renderedText.getSpans(0, holder.renderedText.length, Any::class.java).forEach { span ->
                if (span !is NoCopySpan) holder.renderedText.removeSpan(span)
            }
            holder.renderedText.clear()
            holder.text.minHeight = 0
            holder.measuredHeight = 0
            holder.heightReportPending = false
        }
        if (fullBind || identityChanged) {
            val mine = message.role == AgentMessageRole.USER
            val params = holder.card.layoutParams as FrameLayout.LayoutParams
            params.gravity = if (mine) Gravity.END else Gravity.START
            holder.card.layoutParams = params

            val background = when {
                message.isError -> resolve(com.google.android.material.R.attr.colorError, Color.RED)
                mine -> resolve(com.google.android.material.R.attr.colorPrimary, Color.DKGRAY)
                else -> resolve(com.google.android.material.R.attr.colorSurface, Color.WHITE)
            }
            val foreground = when {
                message.isError -> resolve(com.google.android.material.R.attr.colorOnError, Color.WHITE)
                mine -> resolve(com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)
                else -> resolve(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
            }
            holder.card.setCardBackgroundColor(background)
            holder.card.strokeColor = if (mine || message.isError) background else foreground.withAlpha(32)
            holder.text.setTextColor(foreground)
        }
        holder.card.visibility = if (message.content.isBlank()) View.INVISIBLE else View.VISIBLE
        enqueueMarkdown(holder, message, streaming = false)
    }

    private fun enqueueMarkdown(holder: Holder, message: AgentConversationMessage, streaming: Boolean) {
        val request = RenderRequest(
            bindToken = holder.bindToken.get(),
            sequence = holder.sequence.incrementAndGet(),
            messageId = message.id,
            markdown = message.content,
            streaming = streaming,
        )
        holder.pendingRender.set(request)
        if (holder.renderScheduled.compareAndSet(false, true)) {
            markdownExecutor.execute { drainMarkdown(holder) }
        }
    }

    private fun drainMarkdown(holder: Holder) {
        while (true) {
            val request = holder.pendingRender.getAndSet(null)
            if (request == null) {
                holder.renderScheduled.set(false)
                if (holder.pendingRender.get() != null && holder.renderScheduled.compareAndSet(false, true)) {
                    continue
                }
                return
            }

            val rendered: Spanned? = runCatching { markwon.toMarkdown(request.markdown) }.getOrNull()
            holder.text.post {
                if (holder.bindToken.get() != request.bindToken || holder.boundMessageId != request.messageId) {
                    return@post
                }
                if (request.sequence <= holder.appliedSequence.get()) return@post
                holder.appliedSequence.set(request.sequence)
                if (request.streaming) {
                    holder.text.minHeight = maxOf(holder.text.minHeight, holder.text.height)
                }
                if (holder.measuredHeight == 0 && holder.itemView.height > 0) {
                    holder.measuredHeight = holder.itemView.height
                }
                applyRenderedTail(holder, rendered ?: SpannableString(request.markdown))
                reportHeightAfterLayout(holder, request.messageId, request.streaming)
            }
        }
    }

    private fun applyRenderedTail(holder: Holder, rendered: Spanned) {
        val buffer = holder.renderedText
        val patch = StreamingTextPatchPlanner.calculate(buffer, rendered) ?: return
        buffer.getSpans(patch.start, buffer.length, Any::class.java).forEach { span ->
            if (span !is NoCopySpan) buffer.removeSpan(span)
        }
        buffer.replace(patch.start, patch.oldEnd, rendered, patch.start, patch.newEnd)
    }

    private fun reportHeightAfterLayout(holder: Holder, messageId: String, streaming: Boolean) {
        if (holder.heightReportPending) return
        holder.heightReportPending = true
        holder.itemView.doOnNextLayout { view ->
            holder.heightReportPending = false
            if (holder.boundMessageId != messageId) return@doOnNextLayout
            val previous = holder.measuredHeight
            holder.measuredHeight = view.height
            if (streaming) holder.text.minHeight = maxOf(holder.text.minHeight, holder.text.height)
            if (previous > 0 && view.height != previous) onContentHeightChanged(messageId)
        }
    }

    fun append(message: AgentConversationMessage): Int {
        messages += message
        val position = messages.lastIndex
        notifyItemInserted(position)
        return position
    }

    fun replace(position: Int, message: AgentConversationMessage, streaming: Boolean = false) {
        if (position !in messages.indices) return
        messages[position] = message
        val holder = attachedHolders.firstOrNull { it.boundMessageId == message.id }
        if (holder != null) {
            if (streaming) {
                holder.card.visibility = if (message.content.isBlank()) View.INVISIBLE else View.VISIBLE
                enqueueMarkdown(holder, message, streaming = true)
            } else if (!message.isError) {
                holder.card.visibility = View.VISIBLE
                enqueueMarkdown(holder, message, streaming = false)
            } else {
                bindMessage(holder, message, fullBind = true)
            }
        }
    }

    fun clear() {
        val count = messages.size
        messages.clear()
        if (count > 0) notifyItemRangeRemoved(0, count)
    }

    override fun close() {
        markdownExecutor.shutdownNow()
    }

    private fun resolve(attribute: Int, fallback: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attribute, value, true)) value.data else fallback
    }

    private fun createMarkwon(context: Context): Markwon {
        val primary = resolve(com.google.android.material.R.attr.colorPrimary, Color.rgb(25, 118, 210))
        val codeBackground = resolve(
            com.google.android.material.R.attr.colorSurfaceVariant,
            Color.rgb(0xE6, 0xE6, 0xE9)
        )
        val codeText = resolve(
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            Color.rgb(0x44, 0x44, 0x44)
        )
        val quoteColor = resolve(
            com.google.android.material.R.attr.colorPrimary,
            Color.rgb(25, 118, 210)
        )
        val radius = 8 * context.resources.displayMetrics.density

        return Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .setLinkColor(primary)
                        .setCodeTextColor(codeText)
                        .setCodeBackgroundColor(codeBackground)
                        .setCodeBlockTextColor(codeText)
                        .setCodeBlockBackgroundColor(codeBackground)
                        .setBlockQuoteColor(quoteColor)
                        .setBlockQuoteWidth((3 * context.resources.displayMetrics.density).toInt())
                }

                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    builder.setFactory(CodeBlock::class.java) { _, _ ->
                        arrayOf(RoundedCodeBlockSpan(codeBackground, radius))
                    }
                }
            })
            .build()
    }

    /**
     * Rounded background behind code blocks, replacing Markwon's flat gray rectangle.
     * A [LeadingMarginSpan] so indentation of wrapped lines stays aligned.
     */
    private class RoundedCodeBlockSpan(
        private val backgroundColor: Int,
        private val cornerRadius: Float,
    ) : LeadingMarginSpan, LineBackgroundSpan {
        private val paint = Paint().apply { isAntiAlias = true }

        override fun getLeadingMargin(first: Boolean): Int = 0

        override fun drawLeadingMargin(
            c: Canvas,
            p: Paint,
            x: Int,
            dir: Int,
            top: Int,
            baseline: Int,
            bottom: Int,
            text: CharSequence,
            start: Int,
            end: Int,
            first: Boolean,
            layout: Layout,
        ) = Unit

        override fun drawBackground(
            c: Canvas,
            p: Paint,
            left: Int,
            right: Int,
            top: Int,
            baseline: Int,
            bottom: Int,
            text: CharSequence,
            start: Int,
            end: Int,
            lineNumber: Int,
        ) {
            paint.color = backgroundColor
            c.drawRoundRect(
                left.toFloat(),
                top.toFloat(),
                right.toFloat(),
                bottom.toFloat(),
                cornerRadius,
                cornerRadius,
                paint,
            )
        }
    }

    private fun String.toStableLong(): Long = runCatching {
        UUID.fromString(this).let { it.mostSignificantBits xor it.leastSignificantBits }
    }.getOrElse { hashCode().toLong() }

    private fun Int.withAlpha(alpha: Int): Int = Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))
}
