package com.github.kr328.clash.design.adapter

import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.ConnectionTracker
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.diffWith
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.toBytesString

/** Lists policy hops only; dialer-proxy used inside the leaf is not reported as a chain hop. */
class ConnectionsAdapter(
    private val onClose: (ConnectionTracker) -> Unit,
) : RecyclerView.Adapter<ConnectionsAdapter.Holder>() {
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.conn_root)
        val title: TextView = view.findViewById(R.id.conn_title)
        val subtitle: TextView = view.findViewById(R.id.conn_subtitle)
        val route: TextView = view.findViewById(R.id.conn_route)
        val meta: TextView = view.findViewById(R.id.conn_meta)
        val rule: TextView = view.findViewById(R.id.conn_rule_chip)
        val network: TextView = view.findViewById(R.id.conn_network_chip)
        val details: TextView = view.findViewById(R.id.conn_details)
        val close: ImageButton = view.findViewById(R.id.conn_close)
    }

    private var rows: List<ConnectionTracker> = emptyList()
    private val expandedIds = linkedSetOf<String>()

    fun submit(items: List<ConnectionTracker>) {
        expandedIds.retainAll(items.mapTo(mutableSetOf(), ConnectionTracker::id))
        val diff = rows.diffWith(items, id = ConnectionTracker::id)
        rows = items
        diff.dispatchUpdatesTo(this)
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
        holder.subtitle.text = buildList {
            m?.process?.takeIf { it.isNotBlank() }?.let { add(it) }
            m?.uid?.takeIf { it > 0 }?.let { add("uid $it") }
            m?.network?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
        }.joinToString(" · ").ifBlank { "—" }

        val chain = formatChainLine(c)
        holder.route.text = if (chain == null) {
            ctx.getString(R.string.connections_chain_label, "—")
        } else {
            ctx.getString(R.string.connections_chain_label, chain)
        }

        holder.network.text = m?.network?.uppercase().orEmpty().ifBlank { "—" }
        holder.meta.text = buildString {
            append("↑").append(c.upload.toBytesString())
            append(" · ↓").append(c.download.toBytesString())
        }
        val ruleLine = listOfNotNull(
            c.rule.takeIf { it.isNotBlank() },
            c.rulePayload.takeIf { it.isNotBlank() },
        ).joinToString(" ")
        holder.rule.text = ruleLine.ifBlank { "—" }

        val expanded = c.id in expandedIds
        holder.details.visibility = if (expanded) View.VISIBLE else View.GONE
        holder.details.text = formatDetails(ctx, c)
        holder.root.setOnClickListener {
            if (!expandedIds.add(c.id)) {
                expandedIds.remove(c.id)
            }
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(adapterPosition)
            }
        }
        holder.close.setOnClickListener {
            onClose(c)
        }
    }

    override fun getItemCount(): Int = rows.size

    private fun formatDetails(context: Context, c: ConnectionTracker): String {
        val m = c.metadata
        val lines = mutableListOf<String>()
        if (m != null) {
            val source = endpoint(m.sourceIP, m.sourcePort)
            val destination = endpoint(m.destinationIP, m.destinationPort)
            if (source.isNotBlank() || destination.isNotBlank()) {
                lines += listOf(source.ifBlank { "?" }, destination.ifBlank { "?" }).joinToString(" → ")
            }
            if (m.inboundName.isNotBlank()) lines += context.getString(R.string.connections_inbound_fmt, m.inboundName)
            if (m.processPath.isNotBlank()) lines += m.processPath
            if (m.remoteDestination.isNotBlank()) lines += "Remote: ${m.remoteDestination}"
        }
        if (c.start.isNotBlank()) lines += "Start: ${c.start}"
        if (c.id.isNotBlank()) lines += context.getString(R.string.connections_id_fmt, c.id)
        return lines.joinToString("\n").ifBlank { "—" }
    }

    private fun endpoint(ip: String, port: String): String {
        if (ip.isBlank() && port.isBlank()) return ""
        return if (port.isBlank()) ip else "$ip:$port"
    }

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
