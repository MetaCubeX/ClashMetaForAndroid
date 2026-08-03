package com.github.kr328.clash.agent

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.R
import com.github.kr328.clash.agent.model.AgentConversationMessage
import com.github.kr328.clash.agent.model.AgentMessageRole
import com.google.android.material.card.MaterialCardView

class AgentChatAdapter(
    private val context: Context,
    val messages: MutableList<AgentConversationMessage>,
) : RecyclerView.Adapter<AgentChatAdapter.Holder>() {
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.agent_message_card)
        val text: TextView = view.findViewById(R.id.agent_message_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_agent_message, parent, false)
    )

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val message = messages[position]
        val mine = message.role == AgentMessageRole.USER
        val params = holder.card.layoutParams as FrameLayout.LayoutParams
        params.gravity = if (mine) Gravity.END else Gravity.START
        holder.card.layoutParams = params
        holder.text.text = message.content

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

    fun append(message: AgentConversationMessage): Int {
        messages += message
        val position = messages.lastIndex
        notifyItemInserted(position)
        return position
    }

    fun replace(position: Int, message: AgentConversationMessage) {
        if (position !in messages.indices) return
        messages[position] = message
        notifyItemChanged(position)
    }

    fun clear() {
        val count = messages.size
        messages.clear()
        if (count > 0) notifyItemRangeRemoved(0, count)
    }

    private fun resolve(attribute: Int, fallback: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attribute, value, true)) value.data else fallback
    }

    private fun Int.withAlpha(alpha: Int): Int = Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))
}
