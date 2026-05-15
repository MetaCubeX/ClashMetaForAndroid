package com.github.kr328.clash.design.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.getSystemService
import com.github.kr328.clash.design.R
import com.github.kr328.clash.service.model.YamlPreview
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

fun Context.showYamlPreviewDialog(
    preview: YamlPreview,
    onApply: () -> Unit,
) {
    val padH = dpToPx(24)
    val padTop = dpToPx(4)
    val textPadTop = dpToPx(8)
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padH, padTop, padH, 0)
    }
    val status = TextView(this).apply {
        visibility = if (preview.valid) View.GONE else View.VISIBLE
        text = preview.error ?: getString(R.string.yaml_preview_invalid)
        setTextColor(resolveColor(com.google.android.material.R.attr.colorError))
    }
    val tabs = TabLayout(this)
    val text = TextView(this).apply {
        typeface = android.graphics.Typeface.MONOSPACE
        textSize = 12f
        setTextIsSelectable(true)
        text = preview.proposedYaml
        setPadding(0, textPadTop, 0, 0)
    }
    tabs.addTab(tabs.newTab().setText(R.string.yaml_preview_tab_generated))
    tabs.addTab(tabs.newTab().setText(R.string.yaml_preview_tab_diff))
    tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) {
            text.text = if (tab.position == 0) preview.proposedYaml else preview.diff
        }

        override fun onTabUnselected(tab: TabLayout.Tab) = Unit
        override fun onTabReselected(tab: TabLayout.Tab) = Unit
    })
    val scroll = ScrollView(this).apply {
        addView(text)
    }
    container.addView(status)
    container.addView(tabs)
    container.addView(
        scroll,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            resources.displayMetrics.heightPixels / 2,
        ),
    )

    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(preview.title.ifBlank { getString(R.string.yaml_preview_title) })
        .setView(container)
        .setPositiveButton(R.string.yaml_preview_apply, null)
        .setNegativeButton(R.string.close, null)
        .setNeutralButton(R.string.yaml_preview_copy, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = preview.valid
            setOnClickListener {
                onApply()
                dialog.dismiss()
            }
        }
        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val shown = if (tabs.selectedTabPosition == 0) preview.proposedYaml else preview.diff
            getSystemService<ClipboardManager>()
                ?.setPrimaryClip(ClipData.newPlainText(getString(R.string.yaml_preview_title), shown))
        }
    }
    dialog.show()
}

private fun Context.resolveColor(attr: Int): Int {
    val typedValue = android.util.TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

private fun Context.dpToPx(dp: Int): Int =
    (dp * resources.displayMetrics.density).toInt()
