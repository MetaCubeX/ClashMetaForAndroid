package com.github.kr328.clash.design

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.service.util.TunnelEntry
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText

class TunnelEntryRowsAdapter(
    private val items: MutableList<TunnelEntry>,
    private var proxyOptions: List<String>,
    private val onRowsChanged: () -> Unit,
) : RecyclerView.Adapter<TunnelEntryRowsAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.entry_title)
        val tcp: MaterialCheckBox = view.findViewById(R.id.network_tcp)
        val udp: MaterialCheckBox = view.findViewById(R.id.network_udp)
        val address: TextInputEditText = view.findViewById(R.id.input_address)
        val target: TextInputEditText = view.findViewById(R.id.input_target)
        val proxy: AutoCompleteTextView = view.findViewById(R.id.input_proxy)
        val remove: MaterialButton = view.findViewById(R.id.btn_remove)
        var addressWatcher: TextWatcher? = null
        var targetWatcher: TextWatcher? = null
        var proxyWatcher: TextWatcher? = null
        var tcpListener: ((Boolean) -> Unit)? = null
        var udpListener: ((Boolean) -> Unit)? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tunnel_entry, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = items[position]
        val ctx = holder.itemView.context

        holder.addressWatcher?.let { holder.address.removeTextChangedListener(it) }
        holder.targetWatcher?.let { holder.target.removeTextChangedListener(it) }
        holder.proxyWatcher?.let { holder.proxy.removeTextChangedListener(it) }
        holder.tcpListener?.let { holder.tcp.setOnCheckedChangeListener(null) }
        holder.udpListener?.let { holder.udp.setOnCheckedChangeListener(null) }

        holder.title.text = ctx.getString(R.string.tunnels_entry_title_fmt, position + 1)
        holder.tcp.isChecked = "tcp" in row.network.map { it.lowercase() }
        holder.udp.isChecked = "udp" in row.network.map { it.lowercase() }
        holder.address.setText(row.address)
        holder.target.setText(row.target)
        holder.proxy.setAdapter(
            ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, proxyOptions),
        )
        holder.proxy.setText(row.proxy, false)

        holder.tcpListener = { checked ->
            val pos = holder.bindingAdapterPosition
            if (pos in items.indices) updateNetwork(pos, checked, holder.udp.isChecked)
        }
        holder.udpListener = { checked ->
            val pos = holder.bindingAdapterPosition
            if (pos in items.indices) updateNetwork(pos, holder.tcp.isChecked, checked)
        }
        holder.tcp.setOnCheckedChangeListener { _, checked -> holder.tcpListener?.invoke(checked) }
        holder.udp.setOnCheckedChangeListener { _, checked -> holder.udpListener?.invoke(checked) }

        holder.addressWatcher = textWatcher(holder, holder.address) { pos, text ->
            items[pos] = items[pos].copy(address = text)
        }
        holder.targetWatcher = textWatcher(holder, holder.target) { pos, text ->
            items[pos] = items[pos].copy(target = text)
        }
        holder.proxyWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val pos = holder.bindingAdapterPosition
                if (pos in items.indices) {
                    items[pos] = items[pos].copy(proxy = s?.toString()?.trim().orEmpty())
                    onRowsChanged()
                }
            }
        }
        holder.address.addTextChangedListener(holder.addressWatcher)
        holder.target.addTextChangedListener(holder.targetWatcher)
        holder.proxy.addTextChangedListener(holder.proxyWatcher)

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

    fun setProxyOptions(options: List<String>) {
        proxyOptions = options
        notifyDataSetChanged()
    }

    fun setItems(rows: Collection<TunnelEntry>) {
        items.clear()
        items.addAll(rows.map { it.copy() })
        notifyDataSetChanged()
        onRowsChanged()
    }

    fun addEmptyRow() {
        items.add(
            TunnelEntry(
                network = listOf("tcp", "udp"),
                address = "127.0.0.1:6553",
            ),
        )
        notifyItemInserted(items.size - 1)
        onRowsChanged()
    }

    fun snapshot(): List<TunnelEntry> = items.map { it.copy() }

    private fun updateNetwork(position: Int, tcp: Boolean, udp: Boolean) {
        val net = buildList {
            if (tcp) add("tcp")
            if (udp) add("udp")
        }
        items[position] = items[position].copy(network = net)
        onRowsChanged()
    }

    private fun textWatcher(
        holder: Holder,
        view: TextInputEditText,
        apply: (Int, String) -> Unit,
    ): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            val pos = holder.bindingAdapterPosition
            if (pos in items.indices) {
                apply(pos, s?.toString()?.trim().orEmpty())
                onRowsChanged()
            }
        }
    }
}
