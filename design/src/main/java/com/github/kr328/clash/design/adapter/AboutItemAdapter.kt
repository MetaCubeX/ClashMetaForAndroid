package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.view.ActionLabel

class AboutItemAdapter(
    private val context: Context,
    private val open: (AboutItem) -> Unit,
) : RecyclerView.Adapter<AboutItemAdapter.Holder>() {
    data class AboutItem(
        val id: String,
        @DrawableRes val icon: Int,
        val text: String,
        val subtext: String? = null,
        val clickable: Boolean = true,
    )

    class Holder(val label: ActionLabel) : RecyclerView.ViewHolder(label)

    var items: List<AboutItem> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(ActionLabel(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        })
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val current = items[position]

        holder.label.icon = context.getDrawable(current.icon)
        holder.label.text = current.text
        holder.label.subtext = current.subtext
        holder.label.isEnabled = current.clickable
        holder.label.isClickable = current.clickable
        holder.label.isFocusable = current.clickable
        holder.label.setOnClickListener(if (current.clickable) {
            { open(current) }
        } else {
            null
        })
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
