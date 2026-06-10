package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleProviderItem
import com.github.kr328.clash.service.model.RuleState
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class RulesHubDesign(context: Context) : Design<RulesHubDesign.Request>(context) {
    sealed class Request {
        object Save : Request()
        object AddManual : Request()
        object AddProvider : Request()
        data class EditManual(val ruleId: String) : Request()
        data class EditProvider(val providerId: String) : Request()
        data class ToggleRule(val ruleId: String, val enabled: Boolean) : Request()
        data class ToggleProvider(val providerId: String, val enabled: Boolean) : Request()
        data class RestoreRule(val ruleId: String) : Request()
        data class ReorderManual(val fromId: String, val toId: String) : Request()
    }

    private val rootView: View = context.layoutInflater.inflate(R.layout.design_rules_hub, context.root, false)
    override val root: View get() = rootView

    private val recycler: RecyclerView = rootView.findViewById(R.id.rules_list)
    private val noProfileNotice: TextView = rootView.findViewById(R.id.no_profile_notice)
    private val saveBar: MaterialCardView = rootView.findViewById(R.id.save_bar)
    private val statusText: TextView = rootView.findViewById(R.id.status_text)
    private val btnSave: MaterialButton = rootView.findViewById(R.id.btn_save)

    private var workingState = RuleState()
    private var providerMap: Map<String, RuleProviderItem> = emptyMap()
    private var proxyOptions: List<String> = emptyList()
    private var profileName: String = ""
    private var filter: RulesHubFilter = RulesHubFilter.ALL
    private var searchQuery: String = ""
    private var subscriptionExpanded: Boolean = false
    private var providerDefsExpanded: Boolean = true

    private val adapter = RulesHubListAdapter(
        object : RulesHubListAdapter.Callbacks {
            override fun onSearchChanged(query: String) {
                searchQuery = query
                renderList()
            }

            override fun onFilterChanged(newFilter: RulesHubFilter) {
                filter = newFilter
                renderList()
            }

            override fun onToggleSection(section: RulesHubSection) {
                when (section) {
                    RulesHubSection.SUBSCRIPTION -> subscriptionExpanded = !subscriptionExpanded
                    RulesHubSection.PROVIDER_DEFS -> providerDefsExpanded = !providerDefsExpanded
                    RulesHubSection.MANUAL -> Unit
                }
                renderList()
            }

            override fun onAddManual() {
                requests.trySend(Request.AddManual)
            }

            override fun onAddProvider() {
                requests.trySend(Request.AddProvider)
            }

            override fun onEditManual(ruleId: String) {
                requests.trySend(Request.EditManual(ruleId))
            }

            override fun onToggleRule(ruleId: String, enabled: Boolean) {
                requests.trySend(Request.ToggleRule(ruleId, enabled))
            }

            override fun onRestoreRule(ruleId: String) {
                requests.trySend(Request.RestoreRule(ruleId))
            }

            override fun onReorderManual(fromId: String, toId: String) {
                requests.trySend(Request.ReorderManual(fromId, toId))
            }

            override fun onEditProvider(providerId: String) {
                requests.trySend(Request.EditProvider(providerId))
            }

            override fun onToggleProvider(providerId: String, enabled: Boolean) {
                requests.trySend(Request.ToggleProvider(providerId, enabled))
            }
        },
    )

    init {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPaddingRelative(bars.left, bars.top, bars.right, 0)
            val density = context.resources.displayMetrics.density
            val gap = (8 * density).toInt()
            val barPad = bars.bottom + gap
            saveBar.setPadding(saveBar.paddingLeft, saveBar.paddingTop, saveBar.paddingRight, barPad)
            // Pad the list by the pinned bar's REAL height (measured after it
            // applies its own bottom inset) so it never covers the last rows or
            // a section's expand control. (The old `88` was raw px, ~30dp.)
            saveBar.post {
                recycler.setPadding(
                    recycler.paddingLeft,
                    0,
                    recycler.paddingRight,
                    saveBar.height + gap,
                )
            }
            insets
        }
        rootView.post { ViewCompat.requestApplyInsets(rootView) }

        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter
        recycler.setHasFixedSize(false)
        adapter.attachDragHelper(recycler)

        btnSave.setOnClickListener { requests.trySend(Request.Save) }
    }

    fun showNoProfile() {
        noProfileNotice.visibility = View.VISIBLE
        recycler.visibility = View.GONE
        saveBar.visibility = View.GONE
    }

    fun bind(
        profile: String,
        state: RuleState,
        policies: List<String>,
        expandProviders: Boolean = false,
    ) {
        noProfileNotice.visibility = View.GONE
        recycler.visibility = View.VISIBLE
        saveBar.visibility = View.VISIBLE

        profileName = profile
        workingState = state
        proxyOptions = policies
        providerMap = state.providers.associateBy(RuleProviderItem::name)
        if (expandProviders) {
            subscriptionExpanded = true
            providerDefsExpanded = true
        }
        renderList()
    }

    fun readState(): RuleState {
        val (manual, provider) = RulesHubRowBuilder.partitionRules(workingState.rules)
        return workingState.copy(
            rules = RulesHubRowBuilder.mergeOrderedRules(manual, provider),
        )
    }

    fun knownPolicies(): Set<String> = RulesHubRowBuilder.knownPolicies(proxyOptions)
    fun policyOptions(): List<String> = proxyOptions

    fun mutateRule(id: String, transform: (RuleItem) -> RuleItem) {
        workingState = workingState.copy(
            rules = workingState.rules.map { if (it.id == id) transform(it) else it },
        )
        renderList()
    }

    fun mutateProvider(id: String, transform: (RuleProviderItem) -> RuleProviderItem) {
        workingState = workingState.copy(
            providers = workingState.providers.map { if (it.id == id) transform(it) else it },
        )
        providerMap = workingState.providers.associateBy(RuleProviderItem::name)
        renderList()
    }

    fun addManualRule(rule: RuleItem) {
        val (manual, provider) = RulesHubRowBuilder.partitionRules(workingState.rules)
        workingState = workingState.copy(
            rules = RulesHubRowBuilder.mergeOrderedRules(listOf(rule) + manual, provider),
        )
        renderList()
    }

    fun updateManualRule(id: String, result: RuleEditResult) {
        mutateRule(id) { old ->
            old.copy(
                type = result.type,
                value = result.value,
                policy = result.policy,
                enabled = result.enabled,
                deleted = result.deleted,
            )
        }
    }

    fun deleteManualRule(id: String) {
        mutateRule(id) { it.copy(deleted = true, enabled = false) }
    }

    fun upsertProvider(result: RuleProviderEditResult, existingId: String?) {
        val existing = existingId?.let { id -> workingState.providers.firstOrNull { it.id == id } }
        val item = RuleProviderEditSheet.toProviderItem(result, existing)
        val providers = if (existing != null) {
            workingState.providers.map { if (it.id == existing.id) item else it }
        } else {
            workingState.providers + item
        }
        workingState = workingState.copy(providers = providers)
        providerMap = providers.associateBy(RuleProviderItem::name)
        renderList()
    }

    fun removeProvider(id: String) {
        workingState = workingState.copy(
            providers = workingState.providers.filterNot { it.id == id },
        )
        providerMap = workingState.providers.associateBy(RuleProviderItem::name)
        renderList()
    }

    fun reorderManualById(fromId: String, toId: String) {
        val (manual, provider) = RulesHubRowBuilder.partitionRules(workingState.rules)
        val reordered = RulesHubRowBuilder.reorderManualById(manual, fromId, toId)
        workingState = workingState.copy(
            rules = RulesHubRowBuilder.mergeOrderedRules(reordered, provider),
        )
        renderList()
    }

    fun replaceState(state: RuleState) {
        workingState = state
        providerMap = state.providers.associateBy(RuleProviderItem::name)
        renderList()
    }

    fun setSaveBusy(busy: Boolean) {
        btnSave.isEnabled = !busy
    }

    fun showStatus(message: String?, isError: Boolean) {
        if (message.isNullOrBlank()) {
            statusText.visibility = View.GONE
            return
        }
        statusText.visibility = View.VISIBLE
        statusText.text = message
        statusText.setTextColor(
            MaterialColors.getColor(
                statusText,
                if (isError) com.google.android.material.R.attr.colorError
                else com.google.android.material.R.attr.colorPrimary,
            ),
        )
    }

    fun findRule(id: String): RuleItem? = workingState.rules.firstOrNull { it.id == id }
    fun findProvider(id: String): RuleProviderItem? = workingState.providers.firstOrNull { it.id == id }

    private fun renderList() {
        val items = RulesHubListBuilder.build(
            context = context,
            state = workingState,
            filter = filter,
            searchQuery = searchQuery,
            providerMap = providerMap,
            knownPolicies = knownPolicies(),
            profileName = profileName,
            subscriptionExpanded = subscriptionExpanded,
            providerDefsExpanded = providerDefsExpanded,
        )
        adapter.submit(items, filter, searchQuery)
    }
}
