package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.core.widget.addTextChangedListener
import com.github.kr328.clash.design.adapter.RequestHistoryAdapter
import com.github.kr328.clash.design.databinding.DesignRequestHistoryBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.RequestHistoryEntry
import com.github.kr328.clash.service.model.RequestHistorySnapshot
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RequestHistoryDesign(context: Context) : Design<RequestHistoryDesign.Request>(context) {
    enum class FilterMode {
        ALL,
        ACTIVE,
        CLOSED,
        TCP,
        UDP,
        DIRECT,
        PROXY,
        REJECT,
    }

    sealed class Request {
        object Clear : Request()
        object Export : Request()
    }

    private val binding = DesignRequestHistoryBinding
        .inflate(context.layoutInflater, context.root, false)

    private val adapter = RequestHistoryAdapter()
    private var lastSnapshot = RequestHistorySnapshot()
    private var searchQuery = ""
    private var filterMode = FilterMode.ALL
    private var autoScroll = true

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.requestHistoryList.adapter = adapter
        binding.requestHistorySearch.addTextChangedListener {
            searchQuery = it?.toString().orEmpty()
            applyFilteredList()
        }
        binding.requestHistoryFilterChips.setOnCheckedChangeListener { _, checkedId ->
            filterMode = when (checkedId) {
                R.id.request_filter_active -> FilterMode.ACTIVE
                R.id.request_filter_closed -> FilterMode.CLOSED
                R.id.request_filter_tcp -> FilterMode.TCP
                R.id.request_filter_udp -> FilterMode.UDP
                R.id.request_filter_direct -> FilterMode.DIRECT
                R.id.request_filter_proxy -> FilterMode.PROXY
                R.id.request_filter_reject -> FilterMode.REJECT
                else -> FilterMode.ALL
            }
            applyFilteredList()
        }
        binding.requestHistoryAutoScroll.setOnCheckedChangeListener { _, checked ->
            autoScroll = checked
        }
        binding.btnRequestHistoryClear.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setMessage(R.string.request_history_clear_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.request_history_clear) { _, _ ->
                    requests.trySend(Request.Clear)
                }
                .show()
        }
        binding.btnRequestHistoryExport.setOnClickListener {
            requests.trySend(Request.Export)
        }
        render(RequestHistorySnapshot())
    }

    suspend fun patchSnapshot(snapshot: RequestHistorySnapshot) {
        withContext(Dispatchers.Main) {
            render(snapshot)
        }
    }

    private fun render(snapshot: RequestHistorySnapshot) {
        lastSnapshot = snapshot
        binding.requestHistoryCount.text = context.getString(
            R.string.request_history_count_fmt,
            snapshot.requests.size,
            snapshot.limit,
        )
        binding.btnRequestHistoryClear.isEnabled = snapshot.requests.isNotEmpty()
        binding.btnRequestHistoryExport.isEnabled = snapshot.requests.isNotEmpty()
        applyFilteredList()
    }

    private fun applyFilteredList() {
        val query = searchQuery.trim()
        val rows = lastSnapshot.requests.filter {
            matchesFilter(it) && it.matches(query)
        }
        adapter.submit(rows)
        binding.requestHistoryEmptyHint.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        binding.requestHistoryList.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        if (autoScroll && rows.isNotEmpty()) {
            binding.requestHistoryList.post {
                binding.requestHistoryList.scrollToPosition(rows.lastIndex)
            }
        }
    }

    private fun matchesFilter(entry: RequestHistoryEntry): Boolean {
        val network = entry.network.lowercase()
        val rule = entry.rule.lowercase()
        val proxy = entry.proxy.lowercase()
        val direct = proxy.contains("direct") || rule == "direct"
        val rejected = proxy.contains("reject") || rule.contains("reject")

        return when (filterMode) {
            FilterMode.ALL -> true
            FilterMode.ACTIVE -> entry.status == RequestHistoryEntry.STATUS_ACTIVE
            FilterMode.CLOSED -> entry.status == RequestHistoryEntry.STATUS_CLOSED
            FilterMode.TCP -> network == "tcp"
            FilterMode.UDP -> network == "udp"
            FilterMode.DIRECT -> direct
            FilterMode.PROXY -> !direct && !rejected && proxy.isNotBlank()
            FilterMode.REJECT -> rejected
        }
    }
}
