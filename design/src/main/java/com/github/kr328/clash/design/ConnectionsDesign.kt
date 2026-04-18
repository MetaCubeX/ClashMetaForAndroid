package com.github.kr328.clash.design

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import com.github.kr328.clash.core.model.ConnectionTracker
import com.github.kr328.clash.core.model.ConnectionsSnapshot
import com.github.kr328.clash.design.adapter.ConnectionsAdapter
import com.github.kr328.clash.design.databinding.DesignConnectionsBinding
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConnectionsDesign(context: Context) : Design<ConnectionsDesign.Request>(context) {
    enum class Request {
        OpenLogcat,
    }

    private val binding = DesignConnectionsBinding
        .inflate(context.layoutInflater, context.root, false)

    private val adapter = ConnectionsAdapter()
    private var lastSnapshot: ConnectionsSnapshot = ConnectionsSnapshot()
    private var lastRenderedSignature: String = ""
    private var searchQuery: String = ""
    private var searchJob: Job? = null

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.activityBarLayout.applyFrom(context)
        binding.connectionsList.adapter = adapter
        binding.btnConnectionsOpenLog.setOnClickListener {
            requests.trySend(Request.OpenLogcat)
        }
        binding.connectionsSearch.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    searchQuery = s?.toString().orEmpty()
                    searchJob?.cancel()
                    searchJob = launch {
                        delay(250)
                        // Unconfined scope resumes after delay off the main thread; UI updates must run on Main.
                        withContext(Dispatchers.Main) {
                            applyFilteredList()
                        }
                    }
                }
            },
        )
    }

    suspend fun patchSnapshot(snap: ConnectionsSnapshot) {
        withContext(Dispatchers.Main) {
            val signature = "${snap.uploadTotal}:${snap.downloadTotal}:${snap.connections.size}:${searchQuery}"
            if (signature == lastRenderedSignature) return@withContext
            lastRenderedSignature = signature
            lastSnapshot = snap
            binding.connectionsTotals.text = context.getString(
                R.string.connections_totals_fmt,
                snap.uploadTotal.toBytesString(),
                snap.downloadTotal.toBytesString(),
                snap.connections.size,
            )
            applyFilteredList()
        }
    }

    private fun applyFilteredList() {
        val q = searchQuery.trim().lowercase()
        val list = if (q.isEmpty()) {
            lastSnapshot.connections
        } else {
            lastSnapshot.connections.filter { matchesQuery(it, q) }
        }
        adapter.submit(list)
        val empty = list.isEmpty()
        binding.connectionsEmptyHint.visibility = if (empty) View.VISIBLE else View.GONE
        binding.connectionsList.visibility = if (empty) View.GONE else View.VISIBLE
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
            m.network,
            m.sniffHost,
            m.destinationIP,
            m.sourceIP,
            m.inboundName,
        ).any { it.lowercase().contains(q) }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
