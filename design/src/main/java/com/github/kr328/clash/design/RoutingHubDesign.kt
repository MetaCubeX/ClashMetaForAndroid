package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignRoutingHubBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class RoutingHubDesign(context: Context) : Design<RoutingHubDesign.Request>(context) {
    enum class Request {
        OpenRules,
        OpenPerAppRouting,
        OpenProxyChain,
    }

    private val binding = DesignRoutingHubBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.header.screenTitle.text = context.getString(R.string.nav_routing)

        binding.cardRules.setOnClickListener { requests.trySend(Request.OpenRules) }
        binding.cardProxyChain.setOnClickListener { requests.trySend(Request.OpenProxyChain) }
        binding.cardPerApp.setOnClickListener { requests.trySend(Request.OpenPerAppRouting) }
    }
}
