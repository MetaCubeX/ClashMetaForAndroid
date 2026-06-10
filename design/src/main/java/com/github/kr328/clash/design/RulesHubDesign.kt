package com.github.kr328.clash.design

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleProviderItem
import com.github.kr328.clash.service.model.RuleState
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import kotlin.math.roundToInt

class RulesHubDesign(context: Context) : Design<RulesHubDesign.Request>(context) {
    sealed class Request {
        object Save : Request()
        object AddManual : Request()
        object SaveProviders : Request()
        data class EditManual(val ruleId: String) : Request()
        data class ToggleRule(val ruleId: String, val enabled: Boolean) : Request()
        data class RestoreRule(val ruleId: String) : Request()
        data class ReorderManual(val from: Int, val to: Int) : Request()
    }

    private val rootView: View = context.layoutInflater.inflate(R.layout.design_rules_hub, context.root, false)
    override val root: View get() = rootView

    private val scroll: NestedScrollView = rootView.findViewById(R.id.rules_hub_scroll)
    private val profileName: TextView = rootView.findViewById(R.id.profile_name)
    private val noProfileNotice: TextView = rootView.findViewById(R.id.no_profile_notice)
    private val content: View = rootView.findViewById(R.id.content)
    private val summary: TextView = rootView.findViewById(R.id.rules_summary)
    private val searchInput: TextInputEditText = rootView.findViewById(R.id.rule_search)
    private val filterChips: ChipGroup = rootView.findViewById(R.id.filter_chips)
    private val manualRecycler: RecyclerView = rootView.findViewById(R.id.manual_rules_recycler)
    private val providerRecycler: RecyclerView = rootView.findViewById(R.id.provider_rules_recycler)
    private val providerSectionBody: View = rootView.findViewById(R.id.provider_section_body)
    private val providerSectionSummary: TextView = rootView.findViewById(R.id.provider_section_summary)
    private val providerChevron: ImageView = rootView.findViewById(R.id.provider_chevron)
    private val providersYamlInput: TextInputEditText = rootView.findViewById(R.id.providers_yaml_input)
    private val providerCardsContainer: LinearLayout = rootView.findViewById(R.id.provider_cards_container)
    private val statusText: TextView = rootView.findViewById(R.id.status_text)
    private val btnSave: MaterialButton = rootView.findViewById(R.id.btn_save)
    private val btnAdd: MaterialButton = rootView.findViewById(R.id.btn_add_rule)
    private val btnSaveProviders: MaterialButton = rootView.findViewById(R.id.btn_save_providers)

    private var workingState = RuleState()
    private var providerMap: Map<String, RuleProviderItem> = emptyMap()
    private var proxyOptions: List<String> = emptyList()
    private var filter: RulesHubFilter = RulesHubFilter.ALL
    private var searchQuery: String = ""
    private var providerExpanded: Boolean = false

    private val manualAdapter = RulesHubManualAdapter(
        onToggle = { id, enabled -> requests.trySend(Request.ToggleRule(id, enabled)) },
        onEdit = { id -> requests.trySend(Request.EditManual(id)) },
    )
    private val providerAdapter = RulesHubProviderAdapter(
        onToggle = { id, enabled -> requests.trySend(Request.ToggleRule(id, enabled)) },
        onRestore = { id -> requests.trySend(Request.RestoreRule(id)) },
    )

    private val itemTouchHelper = ItemTouchHelper(
        object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                requests.trySend(Request.ReorderManual(from, to))
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun isLongPressDragEnabled(): Boolean = false
        },
    )

    init {
        rootView.findViewById<TextView>(R.id.screen_title).text =
            context.getString(R.string.rules_hub_title)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPaddingRelative(bars.left, 0, bars.right, 0)
            scroll.setPadding(scroll.paddingLeft, bars.top, scroll.paddingRight, bars.bottom + 16)
            insets
        }
        rootView.post { ViewCompat.requestApplyInsets(rootView) }

        manualRecycler.layoutManager = LinearLayoutManager(context)
        manualRecycler.adapter = manualAdapter
        providerRecycler.layoutManager = LinearLayoutManager(context)
        providerRecycler.adapter = providerAdapter
        itemTouchHelper.attachToRecyclerView(manualRecycler)

        manualRecycler.addOnItemTouchListener(DragHandleTouchListener(itemTouchHelper))

        searchInput.addTextChangedListener {
            searchQuery = it?.toString().orEmpty()
            renderLists()
        }
        filterChips.setOnCheckedStateChangeListener { _, checkedIds ->
            filter = when (checkedIds.firstOrNull()) {
                R.id.chip_filter_mine -> RulesHubFilter.MINE
                R.id.chip_filter_subscription -> RulesHubFilter.SUBSCRIPTION
                R.id.chip_filter_disabled -> RulesHubFilter.DISABLED
                else -> RulesHubFilter.ALL
            }
            renderLists()
        }
        rootView.findViewById<View>(R.id.provider_section_header).setOnClickListener {
            providerExpanded = !providerExpanded
            updateProviderSectionVisibility()
        }
        btnSave.setOnClickListener { requests.trySend(Request.Save) }
        btnAdd.setOnClickListener { requests.trySend(Request.AddManual) }
        btnSaveProviders.setOnClickListener { requests.trySend(Request.SaveProviders) }
    }

    fun showNoProfile() {
        noProfileNotice.visibility = View.VISIBLE
        content.visibility = View.GONE
        profileName.visibility = View.GONE
    }

    fun bind(
        profile: String,
        state: RuleState,
        policies: List<String>,
        providersYaml: String,
        expandProviders: Boolean = false,
    ) {
        noProfileNotice.visibility = View.GONE
        content.visibility = View.VISIBLE
        profileName.visibility = View.VISIBLE
        profileName.text = profile

        workingState = state
        proxyOptions = policies
        providerMap = state.providers.associateBy(RuleProviderItem::name)
        providersYamlInput.setText(providersYaml)
        if (expandProviders) {
            providerExpanded = true
        }
        updateProviderSectionVisibility()
        renderLists()
        renderProviderCards()
    }

    fun readState(): RuleState {
        val (manual, provider) = RulesHubRowBuilder.partitionRules(workingState.rules)
        return workingState.copy(
            rules = RulesHubRowBuilder.mergeOrderedRules(manual, provider),
        )
    }

    fun readProvidersYaml(): String = providersYamlInput.text?.toString().orEmpty()

    fun knownPolicies(): Set<String> = RulesHubRowBuilder.knownPolicies(proxyOptions)

    fun policyOptions(): List<String> = proxyOptions

    fun mutateRule(id: String, transform: (RuleItem) -> RuleItem) {
        workingState = workingState.copy(
            rules = workingState.rules.map { if (it.id == id) transform(it) else it },
        )
        renderLists()
    }

    fun addManualRule(rule: RuleItem) {
        val (manual, provider) = RulesHubRowBuilder.partitionRules(workingState.rules)
        val merged = RulesHubRowBuilder.mergeOrderedRules(listOf(rule) + manual, provider)
        workingState = workingState.copy(rules = merged)
        renderLists()
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

    fun reorderManual(from: Int, to: Int) {
        val visible = filteredManualRules()
        if (from !in visible.indices || to !in visible.indices) return
        val fromId = visible[from].id
        val toId = visible[to].id
        val (manual, provider) = RulesHubRowBuilder.partitionRules(workingState.rules)
        val fromIndex = manual.indexOfFirst { it.id == fromId }
        val toIndex = manual.indexOfFirst { it.id == toId }
        if (fromIndex < 0 || toIndex < 0) return
        val reordered = RulesHubRowBuilder.reorderManual(manual, fromIndex, toIndex)
        workingState = workingState.copy(
            rules = RulesHubRowBuilder.mergeOrderedRules(reordered, provider),
        )
        renderLists()
    }

    fun replaceState(state: RuleState, providersYaml: String) {
        workingState = state
        providerMap = state.providers.associateBy(RuleProviderItem::name)
        providersYamlInput.setText(providersYaml)
        renderLists()
        renderProviderCards()
    }

    fun setSaveBusy(busy: Boolean) {
        btnSave.isEnabled = !busy
        btnSaveProviders.isEnabled = !busy
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

    private fun filteredManualRules(): List<RuleItem> {
        val manual = RulesHubRowBuilder.partitionRules(workingState.rules).first
        return RulesHubRowBuilder.filterRules(manual, filter, searchQuery, providerMap)
    }

    private fun filteredProviderRules(): List<RuleItem> {
        val provider = RulesHubRowBuilder.partitionRules(workingState.rules).second
        return RulesHubRowBuilder.filterRules(provider, filter, searchQuery, providerMap)
    }

    private fun renderLists() {
        val manual = filteredManualRules()
        val provider = filteredProviderRules()
        val allManual = RulesHubRowBuilder.partitionRules(workingState.rules).first
        val allProvider = RulesHubRowBuilder.partitionRules(workingState.rules).second
        val policies = knownPolicies()

        manualAdapter.submit(manual, policies)
        providerAdapter.submit(provider, providerMap)

        summary.text = context.getString(
            R.string.rules_hub_summary_fmt,
            manual.size,
            allManual.size,
            provider.size,
            allProvider.size,
        )
        providerSectionSummary.text = context.getString(
            R.string.rules_hub_provider_section_fmt,
            provider.size,
            allProvider.size,
        )
    }

    private fun updateProviderSectionVisibility() {
        providerSectionBody.visibility = if (providerExpanded) View.VISIBLE else View.GONE
        providerChevron.rotation = if (providerExpanded) 180f else 0f
    }

    private fun renderProviderCards() {
        providerCardsContainer.removeAllViews()
        val providers = workingState.providers
        if (providers.isEmpty()) {
            providerCardsContainer.addView(TextView(context).apply {
                text = context.getString(R.string.rule_existing_providers_empty)
                textSize = 12f
                alpha = 0.7f
            })
            return
        }
        providers.forEach { provider ->
            providerCardsContainer.addView(buildProviderInfoCard(provider))
        }
    }

    private fun buildProviderInfoCard(provider: RuleProviderItem): View {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 12.dp(), 12.dp(), 12.dp())
        }
        content.addView(
            TextView(context).apply {
                text = provider.name
                setTextAppearance(R.style.TextAppearance_App_TitleSmall)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
        )
        if (provider.url.isNotBlank()) {
            content.addView(
                TextView(context).apply {
                    text = provider.url
                    setPadding(0, 6.dp(), 0, 0)
                    setTextAppearance(R.style.TextAppearance_App_BodySmall)
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                },
            )
        }
        content.addView(
            TextView(context).apply {
                val behaviorLabel = provider.behavior.replaceFirstChar { it.uppercaseChar() }
                val typeLabel = provider.type.uppercase()
                text = "$behaviorLabel · $typeLabel"
                setPadding(0, 6.dp(), 0, 0)
                setTextAppearance(R.style.TextAppearance_App_BodySmall)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            },
        )
        return MaterialCardView(context).apply {
            radius = 18f
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = 10.dp() }
            setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh))
            strokeWidth = 1
            strokeColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
            addView(content)
        }
    }

    private fun Int.dp(): Int =
        (this * context.resources.displayMetrics.density).roundToInt()

    private class DragHandleTouchListener(
        private val helper: ItemTouchHelper,
    ) : RecyclerView.SimpleOnItemTouchListener() {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
            if (e.actionMasked != android.view.MotionEvent.ACTION_DOWN) return false
            val child = rv.findChildViewUnder(e.x, e.y) ?: return false
            val handle = child.findViewById<View>(R.id.drag_handle) ?: return false
            val rect = android.graphics.Rect()
            val loc = IntArray(2)
            handle.getDrawingRect(rect)
            val childLoc = IntArray(2)
            child.getLocationOnScreen(childLoc)
            handle.getLocationOnScreen(loc)
            val left = loc[0].toFloat()
            val top = loc[1].toFloat()
            val right = left + handle.width
            val bottom = top + handle.height
            if (e.rawX in left..right && e.rawY in top..bottom) {
                val holder = rv.getChildViewHolder(child)
                helper.startDrag(holder)
                return true
            }
            return false
        }
    }
}
