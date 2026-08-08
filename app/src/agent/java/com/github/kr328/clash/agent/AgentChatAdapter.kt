package com.github.kr328.clash.agent

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.R
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.agent.model.AgentConversationMessage
import com.github.kr328.clash.agent.model.AgentMessageRole
import com.github.kr328.clash.agent.model.AgentTraceEntry
import com.github.kr328.clash.agent.model.AgentTraceStatus
import com.github.kr328.clash.agent.authorization.AgentOperationRisk
import com.github.kr328.clash.agent.tools.AgentExecutableTools
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.ShapeAppearanceModel
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.MarkwonTheme
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
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
    // Resolved once: these attributes are theme-bound, and the adapter is
    // recreated when the activity is.
    private val colorSurfaceVariant =
        resolve(com.google.android.material.R.attr.colorSurfaceVariant, Color.rgb(0xEE, 0xF1, 0xF6))
    private val colorOnSurfaceVariant =
        resolve(com.google.android.material.R.attr.colorOnSurfaceVariant, Color.rgb(0x51, 0x5B, 0x6B))
    private val colorOutline =
        resolve(com.google.android.material.R.attr.colorOutline, Color.rgb(0xD3, 0xDA, 0xE4))
    private val colorSuccess = resolve(DesignR.attr.colorSuccess, Color.rgb(0x1B, 0x7F, 0x4B))
    private val colorWarning = resolve(DesignR.attr.colorWarning, Color.rgb(0xB2, 0x6B, 0x00))
    private val colorError =
        resolve(com.google.android.material.R.attr.colorError, Color.rgb(0xB0, 0x00, 0x20))
    private val colorPrimary =
        resolve(com.google.android.material.R.attr.colorPrimary, Color.rgb(0x19, 0x76, 0xD2))

    private val density = context.resources.displayMetrics.density
    private val strokeWidth = context.resources.getDimensionPixelSize(DesignR.dimen.divider_size)
    private val bubblePaddingHorizontal =
        context.resources.getDimensionPixelSize(R.dimen.agent_bubble_padding_horizontal)
    private val bubblePaddingVertical =
        context.resources.getDimensionPixelSize(R.dimen.agent_bubble_padding_vertical)

    /** A user bubble should never run edge to edge; long prompts stay readable. */
    private val userBubbleMaxWidth =
        (context.resources.displayMetrics.widthPixels * 0.78f).toInt()

    private val bubbleRadius = context.resources.getDimension(R.dimen.agent_bubble_radius)
    private val bubbleTailRadius = context.resources.getDimension(R.dimen.agent_bubble_tail_radius)

    private val userBubbleShape = ShapeAppearanceModel.builder()
        .setAllCornerSizes(bubbleRadius)
        .setBottomRightCornerSize(bubbleTailRadius)
        .build()
    private val assistantBubbleShape = ShapeAppearanceModel.builder()
        .setAllCornerSizes(bubbleRadius)
        .setBottomLeftCornerSize(bubbleTailRadius)
        .build()
    private val flatShape = ShapeAppearanceModel.builder().setAllCornerSizes(0f).build()

    private val markwon = createMarkwon(context)
    private val markdownExecutor = Executors.newSingleThreadExecutor()
    private val attachedHolders = mutableSetOf<Holder>()

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val wrap: LinearLayout = view.findViewById(R.id.agent_message_wrap)
        val card: MaterialCardView = view.findViewById(R.id.agent_message_card)
        val text: MarkdownTextView = view.findViewById(R.id.agent_message_text)
        val traceCard: MaterialCardView = view.findViewById(R.id.agent_trace_card)
        val traceProgress: ProgressBar = view.findViewById(R.id.agent_trace_progress)
        val traceIcon: ImageView = view.findViewById(R.id.agent_trace_icon)
        val traceHeader: View = view.findViewById(R.id.agent_trace_header)
        val traceChevron: ImageView = view.findViewById(R.id.agent_trace_chevron)
        val traceTitle: TextView = view.findViewById(R.id.agent_trace_title)
        val traceCount: TextView = view.findViewById(R.id.agent_trace_count)
        val traceSteps: LinearLayout = view.findViewById(R.id.agent_trace_steps)
        var traceExpanded = false
        var traceBoundMessageId: String? = null
        var boundTrace: List<AgentTraceEntry> = emptyList()
        var boundRole: AgentMessageRole? = null
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
        holder.traceBoundMessageId = null
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
        if (fullBind || identityChanged || holder.boundRole != message.role) {
            holder.boundRole = message.role
            applyBubbleStyle(holder, message)
        }
        holder.card.visibility = if (message.content.isBlank()) View.GONE else View.VISIBLE
        bindTrace(holder, message)
        renderMarkdownNow(holder, message)
    }

    /**
     * Renders on the calling thread, for binds rather than streaming updates.
     *
     * Going through the background executor here meant a freshly bound row was
     * briefly a bubble with no text in it: entering the screen showed the chat
     * history as a column of empty primary-coloured blobs until the parse landed
     * a frame or two later. Only visible rows are bound, so parsing inline costs
     * a few milliseconds and removes the flash entirely.
     */
    private fun renderMarkdownNow(holder: Holder, message: AgentConversationMessage) {
        holder.pendingRender.set(null)
        // Claim the newest sequence so an in-flight background render for this
        // holder can never overwrite what is applied here.
        holder.appliedSequence.set(holder.sequence.incrementAndGet())

        val rendered = runCatching { markwon.toMarkdown(message.content) }.getOrNull()
            ?: SpannableString(message.content)
        applyRenderedTail(holder, rendered)
    }

    /**
     * A user message is a compact right-aligned bubble. An assistant message has
     * no bubble at all: it runs the full width so headings, lists and fenced code
     * are not crammed into a narrow column, which is the main reason long answers
     * used to look cramped.
     */
    private fun applyBubbleStyle(holder: Holder, message: AgentConversationMessage) {
        val mine = message.role == AgentMessageRole.USER
        val error = message.isError

        val wrapParams = holder.wrap.layoutParams as FrameLayout.LayoutParams
        wrapParams.gravity = if (mine) Gravity.END else Gravity.START
        wrapParams.width = if (mine) {
            FrameLayout.LayoutParams.WRAP_CONTENT
        } else {
            FrameLayout.LayoutParams.MATCH_PARENT
        }
        holder.wrap.layoutParams = wrapParams

        val onSurface = resolve(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)

        if (mine) {
            val background = resolve(com.google.android.material.R.attr.colorPrimary, Color.DKGRAY)
            holder.card.shapeAppearanceModel = userBubbleShape
            holder.card.setCardBackgroundColor(background)
            holder.card.strokeWidth = 0
            holder.text.setTextColor(
                resolve(com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)
            )
            holder.text.setPadding(bubblePaddingHorizontal, bubblePaddingVertical, bubblePaddingHorizontal, bubblePaddingVertical)
            holder.text.maxWidth = userBubbleMaxWidth
            // The bubble is already primary; tint the chip with the text colour.
            holder.text.inlineCodeColor = resolve(
                com.google.android.material.R.attr.colorOnPrimary, Color.WHITE
            ).withAlpha(0x33)
        } else if (error) {
            // Keep the failure legible rather than white-on-red: a tinted surface
            // with an error outline reads better for a multi-line message.
            val errorColor = resolve(com.google.android.material.R.attr.colorError, Color.RED)
            holder.card.shapeAppearanceModel = assistantBubbleShape
            holder.card.setCardBackgroundColor(errorColor.withAlpha(0x14))
            holder.card.strokeWidth = strokeWidth
            holder.card.strokeColor = errorColor.withAlpha(0x66)
            holder.text.setTextColor(errorColor)
            holder.text.inlineCodeColor = errorColor.withAlpha(0x1F)
            holder.text.setPadding(bubblePaddingHorizontal, bubblePaddingVertical, bubblePaddingHorizontal, bubblePaddingVertical)
            holder.text.maxWidth = Int.MAX_VALUE
        } else {
            holder.card.shapeAppearanceModel = flatShape
            holder.card.setCardBackgroundColor(Color.TRANSPARENT)
            holder.card.strokeWidth = 0
            holder.text.setTextColor(onSurface)
            holder.text.setPadding(0, 0, 0, 0)
            holder.text.maxWidth = Int.MAX_VALUE
            holder.text.inlineCodeColor = colorPrimary.withAlpha(0x1F)
        }
    }

    private fun bindTrace(holder: Holder, message: AgentConversationMessage) {
        val running = message.running
        val hasToolSteps = message.trace.any {
            it.kind == "tool_start" || it.kind == "tool_done" || it.kind == "tool_error"
        }

        // Show the single-line status card while running (any phase) or when the
        // finished run actually performed tool steps. Finished pure-thinking
        // messages collapse to just the answer bubble.
        val showCard = running || hasToolSteps
        if (message.role != AgentMessageRole.ASSISTANT || !showCard) {
            holder.traceCard.visibility = View.GONE
            holder.traceBoundMessageId = null
            holder.traceProgress.visibility = View.GONE
            return
        }

        val identityChanged = holder.traceBoundMessageId != message.id
        if (identityChanged) {
            holder.traceBoundMessageId = message.id
            // Default to collapsed (single-line summary). User taps to expand.
            holder.traceExpanded = false
        }

        val failed = message.isError || message.trace.any { it.status == AgentTraceStatus.ERROR }

        // While running the header tracks the step actually in flight, so it never
        // sticks on a finished operation. Once done it states the outcome.
        val active = message.trace.lastOrNull { it.status == AgentTraceStatus.RUNNING }
            ?: message.trace.lastOrNull()
        holder.traceTitle.text = when {
            running -> active?.summary?.takeIf(String::isNotEmpty)
                ?: context.getString(R.string.agent_trace_running)
            failed -> context.getString(R.string.agent_trace_failed)
            // Ground truth, derived from the tool results rather than from the
            // answer text: a reply that claims to have changed something during
            // a read-only run is contradicted right here.
            else -> {
                val writes = countSuccessfulWrites(message.trace)
                if (writes > 0) {
                    context.getString(R.string.agent_trace_done_writes, writes)
                } else {
                    context.getString(R.string.agent_trace_done_readonly)
                }
            }
        }

        holder.traceCount.text =
            context.getString(R.string.agent_trace_step_count, message.trace.size)

        holder.traceProgress.visibility = if (running) View.VISIBLE else View.GONE
        holder.traceIcon.visibility = if (running) View.GONE else View.VISIBLE
        if (!running) {
            holder.traceIcon.setImageResource(
                if (failed) R.drawable.ic_agent_step_error else R.drawable.ic_agent_step_done
            )
            holder.traceIcon.imageTintList = ColorStateList.valueOf(
                if (failed) colorError else colorSuccess
            )
        }

        holder.traceCard.setCardBackgroundColor(colorSurfaceVariant)
        holder.traceCard.strokeColor = colorOutline
        holder.traceTitle.setTextColor(colorOnSurfaceVariant)
        holder.traceCount.setTextColor(colorOnSurfaceVariant.withAlpha(0x99))
        holder.traceChevron.imageTintList = ColorStateList.valueOf(colorOnSurfaceVariant)

        if (holder.traceHeader.getTag(R.id.agent_trace_header) == null) {
            holder.traceHeader.setTag(R.id.agent_trace_header, true)
            holder.traceHeader.setOnClickListener {
                holder.traceExpanded = !holder.traceExpanded
                renderTraceExpansion(holder)
                holder.traceBoundMessageId?.let(onContentHeightChanged)
            }
        }
        holder.traceHeader.contentDescription = context.getString(R.string.agent_trace_expand)

        holder.boundTrace = message.trace
        renderTraceExpansion(holder)
    }

    private fun renderTraceExpansion(holder: Holder) {
        holder.traceCard.visibility = View.VISIBLE
        holder.traceChevron.rotation = if (holder.traceExpanded) 180f else 0f
        holder.traceSteps.visibility = if (holder.traceExpanded) View.VISIBLE else View.GONE

        if (!holder.traceExpanded) return

        bindSteps(holder.traceSteps, holder.boundTrace)
    }

    /**
     * Reuses the existing step rows and only adds or removes the difference, so a
     * long-running trace does not re-inflate its whole list on every update.
     */
    private fun bindSteps(container: LinearLayout, trace: List<AgentTraceEntry>) {
        while (container.childCount > trace.size) {
            container.removeViewAt(container.childCount - 1)
        }
        while (container.childCount < trace.size) {
            container.addView(
                LayoutInflater.from(container.context)
                    .inflate(R.layout.item_agent_trace_step, container, false)
            )
        }

        trace.forEachIndexed { index, entry ->
            val row = container.getChildAt(index)
            val progress = row.findViewById<ProgressBar>(R.id.agent_step_progress)
            val icon = row.findViewById<ImageView>(R.id.agent_step_icon)
            val text = row.findViewById<TextView>(R.id.agent_step_text)

            val running = entry.status == AgentTraceStatus.RUNNING
            progress.visibility = if (running) View.VISIBLE else View.GONE
            icon.visibility = if (running) View.GONE else View.VISIBLE

            if (!running) {
                icon.setImageResource(
                    when (entry.status) {
                        AgentTraceStatus.SUCCESS -> R.drawable.ic_agent_step_done
                        AgentTraceStatus.ERROR -> R.drawable.ic_agent_step_error
                        AgentTraceStatus.WARNING -> R.drawable.ic_agent_step_retry
                        else -> R.drawable.ic_agent_step_thinking
                    }
                )
                icon.imageTintList = ColorStateList.valueOf(
                    when (entry.status) {
                        AgentTraceStatus.SUCCESS -> colorSuccess
                        AgentTraceStatus.ERROR -> colorError
                        AgentTraceStatus.WARNING -> colorWarning
                        else -> colorOnSurfaceVariant.withAlpha(0x8A)
                    }
                )
            }

            text.text = entry.summary
            text.setTextColor(
                when (entry.status) {
                    AgentTraceStatus.ERROR -> colorError
                    AgentTraceStatus.RUNNING -> colorOnSurfaceVariant
                    else -> colorOnSurfaceVariant.withAlpha(0xCC)
                }
            )
        }
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
            bindTrace(holder, message)
            if (streaming) {
                // GONE, not INVISIBLE: an empty assistant bubble is borderless, so
                // reserving its line height would leave a gap under the trace card
                // for as long as the model is still thinking.
                holder.card.visibility = if (message.content.isBlank()) View.GONE else View.VISIBLE
                enqueueMarkdown(holder, message, streaming = true)
            } else if (!message.isError) {
                holder.card.visibility = View.VISIBLE
                enqueueMarkdown(holder, message, streaming = false)
            } else {
                bindMessage(holder, message, fullBind = true)
            }
        }
    }

    /**
     * Update only the trace panel (single-line status + expandable log) of a
     * message without touching markdown rendering. Called on every agent state
     * change while streaming so the header always reflects the newest step.
     */
    fun updateTrace(position: Int, trace: List<AgentTraceEntry>, running: Boolean) {
        if (position !in messages.indices) return
        val message = messages[position]
        if (message.trace == trace && message.running == running) return
        messages[position] = message.copy(trace = trace, running = running)
        attachedHolders.firstOrNull { it.boundMessageId == message.id }
            ?.let { bindTrace(it, messages[position]) }
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
        val onSurface = resolve(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val radius = 8 * density

        return Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureVisitor(builder: MarkwonVisitor.Builder) {
                    // Replaces Markwon's inline CodeSpan, whose flat background
                    // span paints a full-line-height rectangle. The surrounding
                    // no-break spaces give the highlight interior padding without
                    // pushing the neighbouring words apart.
                    builder.on(Code::class.java) { visitor, code ->
                        val start = visitor.length()
                        visitor.builder()
                            .append(' ')
                            .append(code.literal)
                            .append(' ')
                        visitor.setSpans(start, InlineCodeSpan(code.literal.isAsciiOnly()))
                    }
                }

                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .linkColor(primary)
                        .codeBlockTextColor(onSurface)
                        // The block background is drawn by RoundedCodeBlockSpan.
                        .codeBlockBackgroundColor(Color.TRANSPARENT)
                        .codeBlockMargin((12 * density).toInt())
                        .blockQuoteColor(primary.withAlpha(0x66))
                        .blockQuoteWidth((3 * density).toInt())
                        .bulletWidth((5 * density).toInt())
                        .listItemColor(colorOnSurfaceVariant)
                        .thematicBreakColor(colorOutline)
                        .thematicBreakHeight((1 * density).toInt())
                        // Markwon underlines h1/h2 by default, which fights with
                        // the surrounding text; size alone carries the hierarchy.
                        .headingBreakHeight(0)
                        .headingTextSizeMultipliers(
                            floatArrayOf(1.4f, 1.25f, 1.12f, 1.0f, 1.0f, 1.0f)
                        )
                }

                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    builder
                        .setFactory(FencedCodeBlock::class.java) { _, _ ->
                            arrayOf(RoundedCodeBlockSpan(colorSurfaceVariant, radius, density))
                        }
                        .setFactory(IndentedCodeBlock::class.java) { _, _ ->
                            arrayOf(RoundedCodeBlockSpan(colorSurfaceVariant, radius, density))
                        }
                }
            })
            .build()
    }

    /**
     * Rounded background behind code blocks, replacing Markwon's flat grey
     * rectangle.
     *
     * [LineBackgroundSpan] is invoked once per line. Drawing a fully rounded rect
     * each time scallops the edges of every multi-line block, so the corner radii
     * are applied only on the line that holds the span's start and the line that
     * holds its end; interior lines get square corners and stack seamlessly.
     */
    private class RoundedCodeBlockSpan(
        private val backgroundColor: Int,
        private val cornerRadius: Float,
        density: Float,
    ) : LeadingMarginSpan, LineBackgroundSpan {
        private val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        private val path = Path()
        private val rect = RectF()
        private val radii = FloatArray(8)
        private val padding = (12 * density).toInt()

        override fun getLeadingMargin(first: Boolean): Int = padding

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
            val spanned = text as? Spanned
            val spanStart = spanned?.getSpanStart(this) ?: -1
            val spanEnd = spanned?.getSpanEnd(this) ?: -1

            val topRadius = if (spanStart in start..end) cornerRadius else 0f
            // The span end may sit just past the final newline, hence the +1.
            val bottomRadius = if (spanEnd in start..(end + 1)) cornerRadius else 0f

            radii[0] = topRadius
            radii[1] = topRadius
            radii[2] = topRadius
            radii[3] = topRadius
            radii[4] = bottomRadius
            radii[5] = bottomRadius
            radii[6] = bottomRadius
            radii[7] = bottomRadius

            rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            path.reset()
            path.addRoundRect(rect, radii, Path.Direction.CW)

            paint.color = backgroundColor
            c.drawPath(path, paint)
        }
    }

    /**
     * Counts steps that actually changed something: a tool that completed and
     * whose declared risk is anything other than read-only. The catalog is the
     * same one the engine exposes to the model, so the classification cannot
     * drift from what was really offered.
     */
    private fun countSuccessfulWrites(trace: List<AgentTraceEntry>): Int = trace.count { entry ->
        entry.status == AgentTraceStatus.SUCCESS &&
            AgentExecutableTools.all.firstOrNull { it.name == entry.toolName }
                ?.risk?.let { it != AgentOperationRisk.READ_ONLY } == true
    }

    /** Monospace suits identifiers and addresses; CJK has no mono glyphs. */
    private fun String.isAsciiOnly(): Boolean = all { it.code in 0x20..0x7E }

    private fun String.toStableLong(): Long = runCatching {
        UUID.fromString(this).let { it.mostSignificantBits xor it.leastSignificantBits }
    }.getOrElse { hashCode().toLong() }

    private fun Int.withAlpha(alpha: Int): Int = Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))

    private companion object {
        const val NBSP = '\u00a0'
    }
}
