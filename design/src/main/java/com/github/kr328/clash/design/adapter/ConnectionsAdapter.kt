package com.github.kr328.clash.design.adapter

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.ConnectionTracker
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.toBytesString

/** Lists policy hops only; dialer-proxy used inside the leaf is not reported as a chain hop. */
class ConnectionsAdapter : RecyclerView.Adapter<ConnectionsAdapter.Holder>() {
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.conn_title)
        val subtitle: TextView = view.findViewById(R.id.conn_subtitle)
        val meta: TextView = view.findViewById(R.id.conn_meta)
    }

    private var rows: List<ConnectionTracker> = emptyList()

    fun submit(items: List<ConnectionTracker>) {
        rows = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = parent.context.layoutInflater.inflate(R.layout.adapter_connection_row, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val c = rows[position]
        val m = c.metadata
        val title = when {
            !m?.sniffHost.isNullOrBlank() -> m!!.sniffHost
            !m?.host.isNullOrBlank() -> m.host
            !m?.destinationIP.isNullOrBlank() -> m.destinationIP
            c.rule.isNotBlank() -> c.rule
            else -> c.id.ifBlank { "—" }
        }
        holder.title.text = title

        val ctx = holder.itemView.context
        val parts = buildList {
            m?.process?.takeIf { it.isNotBlank() }?.let { add(it) }
            formatChainLine(c)?.let { line ->
                add(ctx.getString(R.string.connections_chain_label, line))
            }
            m?.network?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        holder.subtitle.text = parts.joinToString(" · ").ifBlank { "—" }

        val ruleLine = listOfNotNull(
            c.rule.takeIf { it.isNotBlank() },
            c.rulePayload.takeIf { it.isNotBlank() },
        ).joinToString(" ")
        holder.meta.text = buildString {
            append("↑").append(c.upload.toBytesString())
            append(" · ↓").append(c.download.toBytesString())
            if (ruleLine.isNotBlank()) {
                append(" · ").append(ruleLine)
            }
        }
    }

    override fun getItemCount(): Int = rows.size

    /**
     * Merges [ConnectionTracker.chains] with [ConnectionTracker.providerChains] when lengths match
     * (same hop order). If only one list is set, uses that. dialer-proxy is never in these lists.
     */
    private fun formatChainLine(c: ConnectionTracker): String? {
        val ch = c.chains
        val pd = c.providerChains
        if (ch.isEmpty() && pd.isEmpty()) return null
        if (ch.isNotEmpty() && pd.isNotEmpty() && ch.size == pd.size) {
            return ch.mapIndexed { i, name ->
                val tag = pd[i].trim()
                if (tag.isNotEmpty()) "$name [$tag]" else name
            }.joinToString(" › ")
        }
        if (ch.isNotEmpty()) return ch.joinToString(" › ")
        return pd.joinToString(" › ")
    }
}
