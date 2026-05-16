package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.databinding.AdapterAppsStrategyConfigBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.service.model.AppsStrategyConfig
import java.util.*

class AppsStrategyConfigAdapter(
    private val context: Context,
    private val onClicked: (AppsStrategyConfig) -> Unit,
    private val onEditClicked: (AppsStrategyConfig) -> Unit,
    private val onChooseClicked: (AppsStrategyConfig) -> Unit,
) : RecyclerView.Adapter<AppsStrategyConfigAdapter.Holder>() {

    class Holder(val binding: AdapterAppsStrategyConfigBinding) :
        RecyclerView.ViewHolder(binding.root)

    var configs: List<AppsStrategyConfig> = emptyList()
    var activeUuid: UUID? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            AdapterAppsStrategyConfigBinding
                .inflate(context.layoutInflater, parent, false)
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val config = configs[position]
        val binding = holder.binding

        binding.config = config
        binding.isActive = config.uuid == activeUuid
        binding.clicked = View.OnClickListener { onClicked(config) }
        binding.editClicked = View.OnClickListener { onEditClicked(config) }
        binding.chooseClicked = View.OnClickListener { onChooseClicked(config) }
    }

    override fun getItemCount(): Int = configs.size
}
