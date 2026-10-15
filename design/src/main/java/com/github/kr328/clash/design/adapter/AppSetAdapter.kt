package com.github.kr328.clash.design.adapter

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.AdapterAppSetBinding
import com.github.kr328.clash.design.databinding.AdapterAppSetHeaderBinding
import com.github.kr328.clash.design.model.AppSetCheckState
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

internal data class AppSetRow(
    val id: String,
    val title: String,
    val summary: String,
    val iconRes: Int,
    val checkState: AppSetCheckState,
    val enabled: Boolean,
)

internal class AppSetAdapter(
    private val context: Context,
    private val onToggle: (String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var rows: List<AppSetRow> = emptyList()
        private set

    fun submit(rows: List<AppSetRow>) {
        val oldCount = itemCount
        this.rows = rows
        val newCount = itemCount
        if (oldCount == newCount) {
            if (rows.isNotEmpty()) notifyItemRangeChanged(1, rows.size)
        } else {
            if (oldCount > 0) notifyItemRangeRemoved(0, oldCount)
            if (newCount > 0) notifyItemRangeInserted(0, newCount)
        }
    }

    override fun getItemCount(): Int {
        return if (rows.isEmpty()) 0 else rows.size + 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> TYPE_HEADER
            itemCount - 1 -> TYPE_DIVIDER
            else -> TYPE_SET
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(
                AdapterAppSetHeaderBinding.inflate(context.layoutInflater, context.root, false)
            )
            TYPE_DIVIDER -> DividerHolder(
                context.layoutInflater.inflate(R.layout.adapter_app_set_divider, context.root, false)
            )
            else -> SetHolder(
                AdapterAppSetBinding.inflate(context.layoutInflater, context.root, false),
                ContextCompat.getDrawable(context, R.drawable.ic_app_set_indeterminate)?.mutate(),
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderHolder -> holder.binding.title = context.getString(R.string.app_sets)
            is SetHolder -> bindSet(holder, rows[position - 1])
        }
    }

    private fun bindSet(holder: SetHolder, row: AppSetRow) {
        holder.binding.title = row.title
        holder.binding.summary = row.summary
        holder.binding.enabled = row.enabled
        holder.binding.executePendingBindings()
        holder.binding.iconView.setImageResource(row.iconRes)
        val checked = row.checkState == AppSetCheckState.Checked
        holder.binding.switchView.buttonDrawable = if (row.checkState == AppSetCheckState.Indeterminate) {
            holder.indeterminateDrawable
        } else {
            holder.checkboxDrawable
        }
        holder.binding.switchView.isChecked = checked
        holder.binding.root.setOnClickListener {
            if (row.enabled) onToggle(row.id)
        }
    }

    private class HeaderHolder(
        val binding: AdapterAppSetHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    private class SetHolder(
        val binding: AdapterAppSetBinding,
        val indeterminateDrawable: Drawable?,
    ) : RecyclerView.ViewHolder(binding.root) {
        val checkboxDrawable: Drawable? =
            binding.switchView.buttonDrawable?.constantState?.newDrawable()?.mutate()
                ?: binding.switchView.buttonDrawable
    }

    private class DividerHolder(view: View) : RecyclerView.ViewHolder(view)

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_SET = 1
        const val TYPE_DIVIDER = 2
    }
}
