package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.ConcatAdapter
import com.github.kr328.clash.design.adapter.AppAdapter
import com.github.kr328.clash.design.adapter.AppSetAdapter
import com.github.kr328.clash.design.adapter.AppSetRow
import com.github.kr328.clash.design.component.AccessControlMenu
import com.github.kr328.clash.design.databinding.DesignAccessControlBinding
import com.github.kr328.clash.design.databinding.DialogSearchBinding
import com.github.kr328.clash.design.dialog.FullScreenDialog
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.model.AppSetIds
import com.github.kr328.clash.design.model.appSetMembership
import com.github.kr328.clash.design.model.applyAppSetToggle
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class AccessControlDesign(
    context: Context,
    uiStore: UiStore,
    private val selected: MutableSet<String>,
) : Design<AccessControlDesign.Request>(context) {
    enum class Request {
        ReloadApps,
        SelectAll,
        SelectNone,
        SelectInvert,
        Import,
        Export,
    }

    private val binding = DesignAccessControlBinding
        .inflate(context.layoutInflater, context.root, false)

    private val setAdapter = AppSetAdapter(context) { id -> toggleAppSet(id) }

    private val adapter = AppAdapter(context, selected) { refreshSetRows() }

    private var systemPackages: Set<String> = emptySet()

    private var domesticPackages: Set<String> = emptySet()

    private val menu: AccessControlMenu by lazy {
        AccessControlMenu(context, binding.menuView, uiStore, requests)
    }

    val apps: List<AppInfo>
        get() = adapter.apps

    override val root: View
        get() = binding.root

    suspend fun patchApps(apps: List<AppInfo>) {
        adapter.swapDataSet(adapter::apps, apps, false)
    }

    suspend fun patchBuiltinSets(systemPackages: Set<String>, domesticPackages: Set<String>) {
        this.systemPackages = systemPackages
        this.domesticPackages = domesticPackages
        withContext(Dispatchers.Main) {
            setAdapter.submit(buildRows())
        }
    }

    suspend fun rebindAll() {
        withContext(Dispatchers.Main) {
            refreshSetRows()
            adapter.rebindAll()
        }
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.also {
            it.bindAppBarElevation(binding.activityBarLayout)
            it.applyLinearAdapter(context, ConcatAdapter(setAdapter, adapter))
        }

        binding.menuView.setOnClickListener {
            menu.show()
        }

        binding.searchView.setOnClickListener {
            launch {
                try {
                    requestSearch()
                } finally {
                    withContext(NonCancellable) {
                        rebindAll()
                    }
                }
            }
        }
    }

    private suspend fun requestSearch() {
        coroutineScope {
            val binding = DialogSearchBinding
                .inflate(context.layoutInflater, context.root, false)
            val adapter = AppAdapter(context, selected) { refreshSetRows() }
            val dialog = FullScreenDialog(context)
            val filter = Channel<Unit>(Channel.CONFLATED)

            dialog.setContentView(binding.root)

            binding.surface = dialog.surface
            binding.mainList.applyLinearAdapter(context, adapter)
            binding.keywordView.addTextChangedListener {
                filter.trySend(Unit)
            }
            binding.closeView.setOnClickListener {
                dialog.dismiss()
            }

            dialog.setOnDismissListener {
                cancel()
            }

            dialog.setOnShowListener {
                binding.keywordView.requestTextInput()
            }

            dialog.show()

            while (isActive) {
                filter.receive()

                val keyword = binding.keywordView.text?.toString() ?: ""

                val apps: List<AppInfo> = if (keyword.isEmpty()) {
                    emptyList()
                } else {
                    withContext(Dispatchers.Default) {
                        apps.filter {
                            it.label.contains(keyword, ignoreCase = true) ||
                                    it.packageName.contains(keyword, ignoreCase = true)
                        }
                    }
                }

                adapter.patchDataSet(adapter::apps, apps, false, AppInfo::packageName)

                delay(200)
            }
        }
    }

    private fun refreshSetRows() {
        setAdapter.submit(buildRows())
    }

    private fun toggleAppSet(id: String) {
        val members = when (id) {
            AppSetIds.SYSTEM -> systemPackages
            AppSetIds.DOMESTIC -> domesticPackages
            else -> return
        }
        applyAppSetToggle(members, selected)
        refreshSetRows()
        adapter.rebindAll()
    }

    private fun buildRows(): List<AppSetRow> {
        return listOf(
            row(
                AppSetIds.SYSTEM,
                context.getString(R.string.system_apps),
                R.drawable.ic_baseline_apps,
                systemPackages,
            ),
            row(
                AppSetIds.DOMESTIC,
                context.getString(R.string.app_set_domestic),
                R.drawable.ic_baseline_domain,
                domesticPackages,
            ),
        )
    }

    private fun row(id: String, title: String, iconRes: Int, members: Set<String>): AppSetRow {
        val membership = appSetMembership(members, selected)
        val summary = if (members.isEmpty()) {
            context.getString(R.string.app_set_summary_empty)
        } else {
            context.getString(R.string.app_set_summary, membership.total, membership.selectedCount)
        }
        return AppSetRow(
            id = id,
            title = title,
            summary = summary,
            iconRes = iconRes,
            checkState = membership.state,
            enabled = members.isNotEmpty(),
        )
    }
}