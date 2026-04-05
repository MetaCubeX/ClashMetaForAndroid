package com.github.kr328.clash.design.adapter

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.ConnectionTracker
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.toBytesString

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

        val parts = buildList {
            m?.process?.takeIf { it.isNotBlank() }?.let { add(it) }
            when {
                c.chains.isNotEmpty() -> add(c.chains.joinToString(" › "))
                c.providerChains.isNotEmpty() -> add(c.providerChains.joinToString(" › "))
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
}
