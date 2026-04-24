package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.core.widget.addTextChangedListener
import com.github.kr328.clash.design.adapter.AppAdapter
import com.github.kr328.clash.design.component.AccessControlMenu
import com.github.kr328.clash.design.databinding.DesignAccessControlBinding
import com.github.kr328.clash.design.databinding.DialogSearchBinding
import com.github.kr328.clash.design.dialog.FullScreenDialog
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.*
import com.github.kr328.clash.service.model.AccessControlMode
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
        ChangeMode,
    }

    var pendingMode: AccessControlMode? = null
        private set

    /** Latest live filter text from the inline search box. */
    var inlineFilter: String = ""
        private set

    private val binding = DesignAccessControlBinding
        .inflate(context.layoutInflater, context.root, false)

    private val adapter = AppAdapter(context, selected)

    private val menu: AccessControlMenu by lazy {
        AccessControlMenu(context, binding.menuView, uiStore, requests)
    }

    val apps: List<AppInfo>
        get() = adapter.apps

    /** All apps currently loaded by the host (pre-filter). Used for accurate counters. */
    private var allApps: List<AppInfo> = emptyList()

    override val root: View
        get() = binding.root

    suspend fun patchApps(apps: List<AppInfo>) {
        allApps = apps
        val filtered = applyFilterLocked(apps, inlineFilter)
        adapter.swapDataSet(adapter::apps, filtered, false)
        refreshCounter()
    }

    suspend fun rebindAll() {
        withContext(Dispatchers.Main) {
            adapter.rebindAll()
            refreshCounter()
        }
    }

    fun setMode(mode: AccessControlMode) {
        applyModeButtonsState(mode)
    }

    private fun refreshCounter() {
        val total = allApps.size
        val sel = allApps.count { it.packageName in selected }
        binding.accessCounter.text = context.getString(R.string.per_app_counter, sel, total)
    }

    private fun applyFilterLocked(source: List<AppInfo>, keyword: String): List<AppInfo> {
        if (keyword.isBlank()) return source
        return source.filter {
            it.label.contains(keyword, ignoreCase = true) ||
                it.packageName.contains(keyword, ignoreCase = true)
        }
    }

    private fun applyModeButtonsState(mode: AccessControlMode) {
        val id = when (mode) {
            AccessControlMode.AcceptAll -> R.id.access_mode_all
            AccessControlMode.AcceptSelected -> R.id.access_mode_allow
            AccessControlMode.DenySelected -> R.id.access_mode_deny
        }
        if (binding.accessModeGroup.checkedButtonId != id) {
            binding.accessModeGroup.check(id)
        }
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.also {
            it.bindAppBarElevation(binding.activityBarLayout)
            it.applyLinearAdapter(context, adapter)
        }

        // Расширенный bar (toolbar + поиск + переключатель режима) выше дефолтного
        // toolbar_height из common_recycler_list, поэтому верхние карточки списка
        // оказываются под баром. Подгоняем верхний padding RecyclerView под реальную
        // высоту бара после каждой раскладки.
        binding.activityBarLayout.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val recycler = binding.mainList.recyclerList
            if (recycler.paddingTop != v.height) {
                recycler.updatePadding(top = v.height)
            }
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

        binding.inlineSearchInput.addTextChangedListener {
            inlineFilter = it?.toString()?.trim().orEmpty()
            val filtered = applyFilterLocked(allApps, inlineFilter)
            launch {
                adapter.patchDataSet(adapter::apps, filtered, false, AppInfo::packageName)
                refreshCounter()
            }
        }

        binding.accessModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.access_mode_all -> AccessControlMode.AcceptAll
                R.id.access_mode_allow -> AccessControlMode.AcceptSelected
                R.id.access_mode_deny -> AccessControlMode.DenySelected
                else -> return@addOnButtonCheckedListener
            }
            if (pendingMode == mode) return@addOnButtonCheckedListener
            pendingMode = mode
            requests.trySend(Request.ChangeMode)
        }
    }

    private suspend fun requestSearch() {
        coroutineScope {
            val binding = DialogSearchBinding
                .inflate(context.layoutInflater, context.root, false)
            val adapter = AppAdapter(context, selected)
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
                        allApps.filter {
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
}