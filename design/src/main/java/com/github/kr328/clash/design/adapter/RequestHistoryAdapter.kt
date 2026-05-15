package com.github.kr328.clash.design.adapter

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.diffWith
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.service.model.RequestHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RequestHistoryAdapter : RecyclerView.Adapter<RequestHistoryAdapter.Holder>() {
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.request_row_title)
        val subtitle: TextView = view.findViewById(R.id.request_row_subtitle)
        val route: TextView = view.findViewById(R.id.request_row_route)
        val meta: TextView = view.findViewById(R.id.request_row_meta)
        val status: TextView = view.findViewById(R.id.request_row_status)
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var rows: List<RequestHistoryEntry> = emptyList()

    fun submit(items: List<RequestHistoryEntry>) {
        val diff = rows.diffWith(items, id = RequestHistoryEntry::id)
        rows = items
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = parent.context.layoutInflater.inflate(R.layout.adapter_request_history_row, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        val context = holder.itemView.context

        holder.title.text = row.host.ifBlank { row.destination.ifBlank { row.rule.ifBlank { row.id } } }
        holder.subtitle.text = buildList {
            if (row.process.isNotBlank()) add(row.process)
            if (row.uid > 0) add("uid ${row.uid}")
            if (row.network.isNotBlank()) add(row.network.uppercase())
            add(timeFormat.format(Date(row.timestamp)))
        }.joinToString(" · ")
        holder.route.text = context.getString(
            R.string.request_history_route_fmt,
            row.rule.ifBlank { "—" },
            row.rulePayload.ifBlank { "—" },
            row.proxy.ifBlank { "—" },
        )
        holder.meta.text = row.destination.ifBlank { "—" }
        holder.status.text = when (row.status) {
            RequestHistoryEntry.STATUS_ACTIVE -> context.getString(R.string.request_history_status_active)
            RequestHistoryEntry.STATUS_CLOSED -> context.getString(R.string.request_history_status_closed)
            else -> row.status.ifBlank { "—" }
        }
    }

    override fun getItemCount(): Int = rows.size
}
