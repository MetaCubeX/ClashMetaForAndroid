package com.github.kr328.clash.design

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class RulesHubListAdapter(
    private val callbacks: Callbacks,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Callbacks {
        fun onSearchChanged(query: String)
        fun onFilterChanged(filter: RulesHubFilter)
        fun onToggleSection(section: RulesHubSection)
        fun onAddManual()
        fun onAddProvider()
        fun onEditManual(ruleId: String)
        fun onToggleRule(ruleId: String, enabled: Boolean)
        fun onRestoreRule(ruleId: String)
        fun onReorderManual(fromId: String, toId: String)
        fun onEditProvider(providerId: String)
        fun onToggleProvider(providerId: String, enabled: Boolean)
    }

    private var items: List<RulesHubListItem> = emptyList()
    private var filter: RulesHubFilter = RulesHubFilter.ALL
    private var searchQuery: String = ""
    private var headerBound = false

    fun submit(newItems: List<RulesHubListItem>, newFilter: RulesHubFilter, newSearch: String) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
                items[oldPos].stableId == newItems[newPos].stableId
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
                items[oldPos] == newItems[newPos]
        })
        items = newItems
        filter = newFilter
        searchQuery = newSearch
        diff.dispatchUpdatesTo(this)
    }

    fun manualRuleIdAt(position: Int): String? =
        (items.getOrNull(position) as? RulesHubListItem.ManualRule)?.rule?.id

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is RulesHubListItem.Header -> VT_HEADER
        is RulesHubListItem.Section -> VT_SECTION
        is RulesHubListItem.ManualRule -> VT_MANUAL
        is RulesHubListItem.ProviderRule -> VT_PROVIDER_RULE
        is RulesHubListItem.ProviderDef -> VT_PROVIDER_DEF
        is RulesHubListItem.AddAction -> VT_ADD
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VT_HEADER -> HeaderHolder(inflater.inflate(R.layout.item_rules_hub_header, parent, false))
            VT_SECTION -> SectionHolder(inflater.inflate(R.layout.item_rules_hub_section, parent, false))
            VT_MANUAL -> ManualHolder(inflater.inflate(R.layout.item_rules_hub_row_manual, parent, false))
            VT_PROVIDER_RULE -> ProviderRuleHolder(inflater.inflate(R.layout.item_rules_hub_row_provider, parent, false))
            VT_PROVIDER_DEF -> ProviderDefHolder(inflater.inflate(R.layout.item_rules_hub_provider_def, parent, false))
            VT_ADD -> AddHolder(inflater.inflate(R.layout.item_rules_hub_add_action, parent, false))
            else -> error("unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is RulesHubListItem.Header -> bindHeader(holder as HeaderHolder, item)
            is RulesHubListItem.Section -> bindSection(holder as SectionHolder, item)
            is RulesHubListItem.ManualRule -> bindManual(holder as ManualHolder, item)
            is RulesHubListItem.ProviderRule -> bindProviderRule(holder as ProviderRuleHolder, item)
            is RulesHubListItem.ProviderDef -> bindProviderDef(holder as ProviderDefHolder, item)
            is RulesHubListItem.AddAction -> bindAdd(holder as AddHolder, item)
        }
    }

    private fun bindHeader(holder: HeaderHolder, item: RulesHubListItem.Header) {
        holder.title.text = item.profileTitle
        holder.summary.text = item.summary
        if (!headerBound) {
            headerBound = true
            holder.search.setText(searchQuery)
            holder.search.addTextChangedListener { callbacks.onSearchChanged(it?.toString().orEmpty()) }
            holder.chips.setOnCheckedStateChangeListener { _, checkedIds ->
                val f = when (checkedIds.firstOrNull()) {
                    R.id.chip_filter_mine -> RulesHubFilter.MINE
                    R.id.chip_filter_subscription -> RulesHubFilter.SUBSCRIPTION
                    R.id.chip_filter_disabled -> RulesHubFilter.DISABLED
                    else -> RulesHubFilter.ALL
                }
                callbacks.onFilterChanged(f)
            }
        }
        when (filter) {
            RulesHubFilter.MINE -> holder.chips.check(R.id.chip_filter_mine)
            RulesHubFilter.SUBSCRIPTION -> holder.chips.check(R.id.chip_filter_subscription)
            RulesHubFilter.DISABLED -> holder.chips.check(R.id.chip_filter_disabled)
            RulesHubFilter.ALL -> holder.chips.check(R.id.chip_filter_all)
        }
    }

    private fun bindSection(holder: SectionHolder, item: RulesHubListItem.Section) {
        holder.title.text = item.title
        if (item.subtitle.isNullOrBlank()) {
            holder.subtitle.visibility = View.GONE
        } else {
            holder.subtitle.visibility = View.VISIBLE
            holder.subtitle.text = item.subtitle
        }
        if (item.collapsible) {
            holder.chevron.visibility = View.VISIBLE
            holder.chevron.rotation = if (item.expanded) 180f else 0f
            holder.itemView.setOnClickListener { callbacks.onToggleSection(item.section) }
        } else {
            holder.chevron.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = false
        }
    }

    private fun bindManual(holder: ManualHolder, item: RulesHubListItem.ManualRule) {
        val rule = item.rule
        holder.index.text = item.displayIndex.toString()
        holder.line.text = RulesHubRowBuilder.buildRuleLine(rule)
        holder.line.alpha = when {
            rule.deleted -> 0.55f
            !rule.enabled -> 0.75f
            else -> 1f
        }
        holder.targetMissing.visibility = if (item.targetMissing) View.VISIBLE else View.GONE
        holder.enabled.setOnCheckedChangeListener(null)
        holder.enabled.isChecked = rule.enabled
        holder.enabled.isEnabled = !rule.deleted
        holder.enabled.setOnCheckedChangeListener { _, checked ->
            callbacks.onToggleRule(rule.id, checked)
        }
        holder.edit.setOnClickListener { callbacks.onEditManual(rule.id) }
    }

    private fun bindProviderRule(holder: ProviderRuleHolder, item: RulesHubListItem.ProviderRule) {
        val rule = item.rule
        holder.index.text = item.displayIndex.toString()
        holder.line.text = RulesHubRowBuilder.buildRuleLine(rule)
        holder.line.alpha = when {
            rule.deleted -> 0.55f
            !rule.enabled -> 0.75f
            else -> 1f
        }
        holder.meta.text = item.meta
        holder.enabled.setOnCheckedChangeListener(null)
        holder.enabled.isChecked = rule.enabled
        holder.enabled.isEnabled = !rule.deleted
        holder.enabled.setOnCheckedChangeListener { _, checked ->
            callbacks.onToggleRule(rule.id, checked)
        }
        val showRestore = rule.deleted && rule.isRestorable
        holder.restore.visibility = if (showRestore) View.VISIBLE else View.GONE
        holder.restore.setOnClickListener { callbacks.onRestoreRule(rule.id) }
    }

    private fun bindProviderDef(holder: ProviderDefHolder, item: RulesHubListItem.ProviderDef) {
        val p = item.provider
        holder.name.text = p.name
        holder.url.text = p.url
        val hours = (p.interval / 3600).coerceAtLeast(1)
        val behavior = p.behavior.replaceFirstChar { it.uppercaseChar() }
        holder.meta.text = holder.itemView.context.getString(
            R.string.rules_hub_provider_meta_fmt,
            behavior,
            hours,
        )
        holder.enabled.setOnCheckedChangeListener(null)
        holder.enabled.isChecked = p.enabled
        holder.enabled.setOnCheckedChangeListener { _, checked ->
            callbacks.onToggleProvider(p.id, checked)
        }
        holder.edit.setOnClickListener { callbacks.onEditProvider(p.id) }
        holder.itemView.alpha = if (p.enabled) 1f else 0.7f
    }

    private fun bindAdd(holder: AddHolder, item: RulesHubListItem.AddAction) {
        holder.button.text = item.label
        holder.button.setOnClickListener {
            when (item.action) {
                RulesHubListItem.AddActionKind.MANUAL -> callbacks.onAddManual()
                RulesHubListItem.AddActionKind.PROVIDER -> callbacks.onAddProvider()
            }
        }
    }

    fun attachDragHelper(recycler: RecyclerView): ItemTouchHelper {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(UP or DOWN, 0) {
            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                if (vh !is ManualHolder) return 0
                return makeMovementFlags(UP or DOWN, 0)
            }

            override fun onMove(
                rv: RecyclerView,
                src: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                if (src !is ManualHolder || target !is ManualHolder) return false
                val fromId = manualRuleIdAt(src.bindingAdapterPosition) ?: return false
                val toId = manualRuleIdAt(target.bindingAdapterPosition) ?: return false
                callbacks.onReorderManual(fromId, toId)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) = Unit
        })
        helper.attachToRecyclerView(recycler)
        recycler.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.actionMasked != MotionEvent.ACTION_DOWN) return false
                val child = rv.findChildViewUnder(e.x, e.y) ?: return false
                val handle = child.findViewById<View>(R.id.drag_handle) ?: return false
                val loc = IntArray(2)
                handle.getLocationOnScreen(loc)
                val left = loc[0].toFloat()
                val top = loc[1].toFloat()
                if (e.rawX in left..(left + handle.width) && e.rawY in top..(top + handle.height)) {
                    val holder = rv.getChildViewHolder(child)
                    if (holder is ManualHolder) helper.startDrag(holder)
                    return true
                }
                return false
            }
        })
        return helper
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.screen_title)
        val summary: TextView = view.findViewById(R.id.rules_summary)
        val search: TextInputEditText = view.findViewById(R.id.rule_search)
        val chips: ChipGroup = view.findViewById(R.id.filter_chips)
    }

    private class SectionHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.section_title)
        val subtitle: TextView = view.findViewById(R.id.section_subtitle)
        val chevron: ImageView = view.findViewById(R.id.section_chevron)
    }

    private class ManualHolder(view: View) : RecyclerView.ViewHolder(view) {
        val index: TextView = view.findViewById(R.id.rule_index)
        val line: TextView = view.findViewById(R.id.rule_line)
        val targetMissing: TextView = view.findViewById(R.id.target_missing)
        val enabled: MaterialSwitch = view.findViewById(R.id.rule_enabled_switch)
        val edit: ImageButton = view.findViewById(R.id.btn_edit)
    }

    private class ProviderRuleHolder(view: View) : RecyclerView.ViewHolder(view) {
        val index: TextView = view.findViewById(R.id.rule_index)
        val line: TextView = view.findViewById(R.id.rule_line)
        val meta: TextView = view.findViewById(R.id.rule_meta)
        val enabled: MaterialSwitch = view.findViewById(R.id.rule_enabled_switch)
        val restore: ImageButton = view.findViewById(R.id.btn_restore)
    }

    private class ProviderDefHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.provider_name)
        val url: TextView = view.findViewById(R.id.provider_url)
        val meta: TextView = view.findViewById(R.id.provider_meta)
        val enabled: MaterialSwitch = view.findViewById(R.id.provider_enabled_switch)
        val edit: ImageButton = view.findViewById(R.id.btn_edit_provider)
    }

    private class AddHolder(view: View) : RecyclerView.ViewHolder(view) {
        val button: MaterialButton = view.findViewById(R.id.btn_action)
    }

    private companion object {
        const val VT_HEADER = 0
        const val VT_SECTION = 1
        const val VT_MANUAL = 2
        const val VT_PROVIDER_RULE = 3
        const val VT_PROVIDER_DEF = 4
        const val VT_ADD = 5
        const val UP = ItemTouchHelper.UP
        const val DOWN = ItemTouchHelper.DOWN
    }
}
