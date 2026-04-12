package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.adapter.AboutItemAdapter
import com.github.kr328.clash.design.databinding.DesignAboutPageBinding
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.patchDataSet
import com.github.kr328.clash.design.util.root

class AboutDesign(context: Context) : Design<AboutDesign.Request>(context) {
    enum class Request {
        OpenHelp,
        OpenSource,
    }

    private val binding = DesignAboutPageBinding
        .inflate(context.layoutInflater, context.root, false)
    private val adapter = AboutItemAdapter(context) {
        when (it.id) {
            "help" -> request(Request.OpenHelp)
            "source" -> request(Request.OpenSource)
        }
    }

    override val root: View
        get() = binding.root

    suspend fun patchItems(items: List<AboutItemAdapter.AboutItem>) {
        adapter.patchDataSet(adapter::items, items, false, AboutItemAdapter.AboutItem::id)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    init {
        binding.self = this
        binding.recyclerList.applyLinearAdapter(context, adapter)
    }
}
