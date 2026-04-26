package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.core.widget.addTextChangedListener
import com.github.kr328.clash.core.model.ConnectionTracker
import com.github.kr328.clash.core.model.ConnectionsSnapshot
import com.github.kr328.clash.design.adapter.ConnectionsAdapter
import com.github.kr328.clash.design.databinding.DesignConnectionsBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.util.toBytesString
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConnectionsDesign(context: Context) : Design<ConnectionsDesign.Request>(context) {
    enum class FilterMode {
        ALL,
        TCP,
        UDP,
        DIRECT,
        PROXY,
        REJECT,
    }

    sealed class Request {
        object OpenLogcat : Request()
        data class CloseConnection(val id: String) : Request()
        object CloseAllConnections : Request()
    }

    private val binding = DesignConnectionsBinding
        .inflate(context.layoutInflater, context.root, false)

    private val adapter = ConnectionsAdapter { connection ->
        if (connection.id.isNotBlank()) {
            requests.trySend(Request.CloseConnection(connection.id))
        }
    }
    private var lastSnapshot: ConnectionsSnapshot = ConnectionsSnapshot()
    private var searchQuery: String = ""
    private var filterMode: FilterMode = FilterMode.ALL

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.connectionsList.adapter = adapter
        binding.btnConnectionsOpenLog.setOnClickListener {
            requests.trySend(Request.OpenLogcat)
        }
        binding.btnConnectionsCloseAll.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setMessage(R.string.connections_close_all_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.connections_close_all) { _, _ ->
                    requests.trySend(Request.CloseAllConnections)
                }
                .show()
        }
        binding.connectionsSearch.addTextChangedListener {
            searchQuery = it?.toString().orEmpty()
            applyFilteredList()
        }
        binding.connectionsFilterChips.setOnCheckedChangeListener { _, checkedId ->
            filterMode = when (checkedId) {
                R.id.filter_tcp -> FilterMode.TCP
                R.id.filter_udp -> FilterMode.UDP
                R.id.filter_direct -> FilterMode.DIRECT
                R.id.filter_proxy -> FilterMode.PROXY
                R.id.filter_reject -> FilterMode.REJECT
                else -> FilterMode.ALL
            }
            applyFilteredList()
        }
        renderSnapshot(ConnectionsSnapshot())
    }

    suspend fun patchSnapshot(snap: ConnectionsSnapshot) {
        withContext(Dispatchers.Main) {
            renderSnapshot(snap)
        }
    }

    private fun renderSnapshot(snap: ConnectionsSnapshot) {
        lastSnapshot = snap
        binding.connectionsActiveValue.text = snap.connections.size.toString()
        binding.connectionsUploadValue.text = snap.uploadTotal.toBytesString()
        binding.connectionsDownloadValue.text = snap.downloadTotal.toBytesString()
        binding.connectionsMemoryValue.text = snap.memory.toBytesString()
        binding.btnConnectionsCloseAll.isEnabled = snap.connections.isNotEmpty()
        applyFilteredList()
    }

    private fun applyFilteredList() {
        val q = searchQuery.trim().lowercase()
        val list = lastSnapshot.connections.filter { connection ->
            matchesFilter(connection) && (q.isEmpty() || matchesQuery(connection, q))
        }
        adapter.submit(list)
        val empty = list.isEmpty()
        binding.connectionsEmptyHint.visibility = if (empty) View.VISIBLE else View.GONE
        binding.connectionsList.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun matchesFilter(c: ConnectionTracker): Boolean {
        val network = c.metadata?.network?.lowercase().orEmpty()
        val rule = c.rule.lowercase()
        val chain = c.chains.joinToString(" ").lowercase()
        val providerChain = c.providerChains.joinToString(" ").lowercase()
        val direct = rule == "direct" || c.chains.any { it.equals("direct", ignoreCase = true) }
        val rejected = rule.contains("reject") ||
            chain.contains("reject") ||
            providerChain.contains("reject")
        return when (filterMode) {
            FilterMode.ALL -> true
            FilterMode.TCP -> network == "tcp"
            FilterMode.UDP -> network == "udp"
            FilterMode.DIRECT -> direct
            FilterMode.PROXY -> !direct && !rejected && (c.chains.isNotEmpty() || c.providerChains.isNotEmpty())
            FilterMode.REJECT -> rejected
        }
    }

    private fun matchesQuery(c: ConnectionTracker, q: String): Boolean {
        if (c.id.lowercase().contains(q)) return true
        if (c.rule.lowercase().contains(q)) return true
        if (c.rulePayload.lowercase().contains(q)) return true
        if (c.chains.any { it.lowercase().contains(q) }) return true
        if (c.providerChains.any { it.lowercase().contains(q) }) return true
        val m = c.metadata ?: return false
        return sequenceOf(
            m.host,
            m.process,
            m.processPath,
            m.network,
            m.sniffHost,
            m.destinationIP,
            m.sourceIP,
            m.inboundName,
            m.remoteDestination,
        ).any { it.lowercase().contains(q) }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
