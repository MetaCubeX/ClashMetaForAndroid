package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.util.ProxyProviderUiRow
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProxyProvidersEditorDesign(
    context: Context,
) : Design<ProxyProvidersEditorDesign.Request>(context) {
    enum class Request {
        Save,
        UpdateAllProviders,
        OpenMergedGroupConstructor,
    }

    private val rows = mutableListOf<ProxyProviderUiRow>()
    private val adapter = ProxyProviderRowsAdapter(rows) { }

    private val rootView: View = context.layoutInflater.inflate(
        R.layout.design_proxy_providers_editor,
        context.root,
        false,
    )

    private val toolbar: MaterialToolbar = rootView.findViewById(R.id.toolbar)
    private val spinnerSubscriptions: AutoCompleteTextView = rootView.findViewById(R.id.spinner_subscriptions)
    private val btnAddSelected: MaterialButton = rootView.findViewById(R.id.btn_add_selected)

    private var subscriptionPicks: List<SubscriptionPick> = emptyList()

    override val root: View
        get() = rootView

    init {
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
            insets
        }
        rootView.post {
            ViewCompat.requestApplyInsets(rootView)
        }

        toolbar.inflateMenu(R.menu.menu_proxy_providers_editor)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_merged_proxy_constructor -> {
                    requests.trySend(Request.OpenMergedGroupConstructor)
                    true
                }
                R.id.action_show_hint -> {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.proxy_providers_editor_hint_title)
                        .setMessage(R.string.proxy_providers_editor_hint)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    true
                }
                else -> false
            }
        }

        rootView.findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ProxyProvidersEditorDesign.adapter
        }
        toolbar.setNavigationOnClickListener {
            (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
        }
        rootView.findViewById<View>(R.id.btn_add).setOnClickListener {
            adapter.addEmptyRow()
            scrollToLastRow()
        }
        rootView.findViewById<View>(R.id.btn_save).setOnClickListener {
            requests.trySend(Request.Save)
        }
        rootView.findViewById<View>(R.id.btn_update_all).setOnClickListener {
            requests.trySend(Request.UpdateAllProviders)
        }
        btnAddSelected.setOnClickListener {
            val pos = (spinnerSubscriptions.adapter as ArrayAdapter<String>).getPosition(spinnerSubscriptions.text.toString())
            if (pos !in subscriptionPicks.indices) return@setOnClickListener
            val pick = subscriptionPicks[pos]
            adapter.addRow(
                ProxyProviderUiRow(
                    title = pick.rowTitle,
                    url = pick.url,
                    intervalSeconds = pick.intervalSeconds,
                ),
            )
            scrollToLastRow()
        }
    }

    fun setSubscriptionPicks(picks: List<SubscriptionPick>) {
        subscriptionPicks = picks
        val labels = picks.map { it.label }
        spinnerSubscriptions.setAdapter(
            ArrayAdapter(
                context,
                android.R.layout.simple_dropdown_item_1line,
                labels,
            )
        )
        val has = picks.isNotEmpty()
        spinnerSubscriptions.isEnabled = has
        btnAddSelected.isEnabled = has
    }

    fun setRows(list: List<ProxyProviderUiRow>) {
        adapter.setItems(list)
    }

    fun getRows(): List<ProxyProviderUiRow> = adapter.snapshot()

    fun appendRow(row: ProxyProviderUiRow) {
        adapter.addRow(row)
    }

    fun scrollToLastRow() {
        val n = adapter.itemCount
        if (n > 0) {
            rootView.findViewById<RecyclerView>(R.id.recycler)
                .smoothScrollToPosition(n - 1)
        }
    }
}
