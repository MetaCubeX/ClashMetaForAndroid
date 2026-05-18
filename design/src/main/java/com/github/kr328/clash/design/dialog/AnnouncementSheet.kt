package com.github.kr328.clash.design.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.github.kr328.clash.design.R
import com.google.android.material.button.MaterialButton

/**
 * Bottom sheet showing the full announcement payload of an active subscription.
 *
 * Trigger is the compact banner on the home screen — opening this sheet also
 * persists the read-hash so the banner stops flagging `New` for the same
 * announcement. See [com.github.kr328.clash.design.MainDesign.setAnnouncement].
 */
object AnnouncementSheet {
    fun show(
        context: Context,
        text: String,
        url: String?,
        supportUrl: String?,
        sourceName: String?,
        onOpenUrl: (String) -> Unit,
        onSupport: (() -> Unit)?,
    ) {
        val dialog = AppBottomSheetDialog(context, fitContentHeight = true)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_announcement, null, false)

        val source = view.findViewById<TextView>(R.id.announcement_sheet_source)
        if (sourceName.isNullOrBlank()) {
            source.visibility = View.GONE
        } else {
            source.visibility = View.VISIBLE
            source.text = context.getString(R.string.announcement_sheet_from, sourceName)
        }

        view.findViewById<TextView>(R.id.announcement_sheet_body).text = text

        val openLink = view.findViewById<MaterialButton>(R.id.announcement_sheet_open_link)
        if (url.isNullOrBlank()) {
            openLink.visibility = View.GONE
        } else {
            openLink.visibility = View.VISIBLE
            openLink.setOnClickListener {
                onOpenUrl(url)
                dialog.dismiss()
            }
        }

        val support = view.findViewById<MaterialButton>(R.id.announcement_sheet_support)
        if (supportUrl.isNullOrBlank() && onSupport == null) {
            support.visibility = View.GONE
        } else {
            support.visibility = View.VISIBLE
            support.setOnClickListener {
                onSupport?.invoke() ?: supportUrl?.let { onOpenUrl(it) }
                dialog.dismiss()
            }
        }

        view.findViewById<MaterialButton>(R.id.announcement_sheet_copy).setOnClickListener {
            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clip?.setPrimaryClip(ClipData.newPlainText("announcement", text))
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.announcement_copied),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }

        // Squeeze the action row if only one button is visible — keeps layout balanced.
        val actions = (openLink.parent as? LinearLayout)
        actions?.let { row ->
            val visibleCount = (0 until row.childCount).count { row.getChildAt(it).visibility == View.VISIBLE }
            if (visibleCount <= 1) {
                for (i in 0 until row.childCount) {
                    val child = row.getChildAt(i)
                    if (child.visibility == View.VISIBLE) {
                        (child.layoutParams as? LinearLayout.LayoutParams)?.weight = 0f
                        (child.layoutParams as? LinearLayout.LayoutParams)?.width =
                            LinearLayout.LayoutParams.MATCH_PARENT
                    }
                }
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }
}
