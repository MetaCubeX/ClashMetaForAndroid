package com.github.kr328.clash.design

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.github.kr328.clash.service.util.ProxyProviderUiRow

/**
 * Editable list of HTTP proxy-provider rows; keys on save are always sub1..subN.
 */
class ProxyProviderRowsAdapter(
    private val items: MutableList<ProxyProviderUiRow>,
    private val onRowsChanged: () -> Unit,
) : RecyclerView.Adapter<ProxyProviderRowsAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextInputEditText = view.findViewById(R.id.input_title)
        val url: TextInputEditText = view.findViewById(R.id.input_url)
        val interval: TextInputEditText = view.findViewById(R.id.input_interval)
        val remove: MaterialButton = view.findViewById(R.id.btn_remove)
        var titleWatcher: TextWatcher? = null
        var urlWatcher: TextWatcher? = null
        var intervalWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_proxy_provider_row, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = items[position]
        holder.titleWatcher?.let { holder.title.removeTextChangedListener(it) }
        holder.urlWatcher?.let { holder.url.removeTextChangedListener(it) }
        holder.intervalWatcher?.let { holder.interval.removeTextChangedListener(it) }

        holder.title.setText(row.title)
        holder.url.setText(row.url)
        holder.interval.setText(
            if (row.intervalSeconds > 0) row.intervalSeconds.toString() else "3600",
        )

        holder.titleWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val pos = holder.bindingAdapterPosition
                if (pos in items.indices) {
                    items[pos] = items[pos].copy(title = s?.toString()?.trim().orEmpty())
                    onRowsChanged()
                }
            }
        }
        holder.urlWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val pos = holder.bindingAdapterPosition
                if (pos in items.indices) {
                    items[pos] = items[pos].copy(url = s?.toString()?.trim().orEmpty())
                    onRowsChanged()
                }
            }
        }
        holder.intervalWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val pos = holder.bindingAdapterPosition
                if (pos in items.indices) {
                    val sec = s?.toString()?.trim()?.toLongOrNull() ?: 3600L
                    items[pos] = items[pos].copy(intervalSeconds = sec.coerceAtLeast(60L))
                    onRowsChanged()
                }
            }
        }
        holder.title.addTextChangedListener(holder.titleWatcher)
        holder.url.addTextChangedListener(holder.urlWatcher)
        holder.interval.addTextChangedListener(holder.intervalWatcher)

        holder.remove.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos in items.indices) {
                items.removeAt(pos)
                notifyItemRemoved(pos)
                notifyItemRangeChanged(pos, items.size - pos)
                onRowsChanged()
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun setItems(rows: Collection<ProxyProviderUiRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
        onRowsChanged()
    }

    fun addEmptyRow() {
        items.add(
            ProxyProviderUiRow(
                title = "",
                url = "https://",
                intervalSeconds = 3600L,
            ),
        )
        notifyItemInserted(items.size - 1)
        onRowsChanged()
    }

    fun addRow(row: ProxyProviderUiRow) {
        items.add(row.copy())
        notifyItemInserted(items.size - 1)
        onRowsChanged()
    }

    fun snapshot(): List<ProxyProviderUiRow> = items.map { it.copy() }
}
