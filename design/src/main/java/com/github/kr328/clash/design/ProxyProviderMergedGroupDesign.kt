package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.google.android.material.appbar.MaterialToolbar

class ProxyProviderMergedGroupDesign(
    context: Context,
) : Design<ProxyProviderMergedGroupDesign.Request>(context) {
    sealed class Request {
        data object AddMergedSelectGroup : Request()
        data class RemoveMergedGroup(val name: String) : Request()
    }

    private val rootView: View = context.layoutInflater.inflate(
        R.layout.design_proxy_provider_merged_group,
        context.root,
        false,
    )

    private val toolbar: MaterialToolbar = rootView.findViewById(R.id.toolbar)
    val summaryKeys: TextView = rootView.findViewById(R.id.provider_keys_summary)
    private val mergedExistingEmpty: TextView = rootView.findViewById(R.id.merged_existing_empty)
    private val mergedExistingList: LinearLayout = rootView.findViewById(R.id.merged_existing_list)

    override val root: View
        get() = rootView

    init {
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
            insets
        }
        rootView.post { ViewCompat.requestApplyInsets(rootView) }

        toolbar.setNavigationOnClickListener {
            (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
        }
        rootView.findViewById<View>(R.id.btn_add_merged_select).setOnClickListener {
            requests.trySend(Request.AddMergedSelectGroup)
        }
    }

    fun setExistingGroups(names: List<String>) {
        mergedExistingList.removeAllViews()
        mergedExistingEmpty.visibility = if (names.isEmpty()) View.VISIBLE else View.GONE
        if (names.isEmpty()) return
        val inflater = context.layoutInflater
        for (name in names) {
            val row = inflater.inflate(R.layout.design_proxy_provider_merged_existing_row, mergedExistingList, false)
            row.findViewById<TextView>(R.id.group_name).text = name
            row.findViewById<View>(R.id.btn_remove).setOnClickListener {
                requests.trySend(Request.RemoveMergedGroup(name))
            }
            mergedExistingList.addView(row)
        }
    }
}
