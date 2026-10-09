package com.github.kr328.clash.design.dialog

import android.content.Context
import android.content.DialogInterface
import android.view.View
import com.github.kr328.clash.design.databinding.DialogSyncLogBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ModelLogDialogScope {
    suspend fun log(line: CharSequence)
    suspend fun progress(current: Int, max: Int)
}

/**
 * Shows a dialog with a progress bar and a running log while [block] runs.
 *
 * Unlike [withModelProgressBar] the dialog stays open afterwards, so the user can read
 * the final log; it only becomes dismissible once [block] has finished.
 */
suspend fun Context.withModelLogDialog(title: CharSequence, block: suspend ModelLogDialogScope.() -> Unit) {
    val view = DialogSyncLogBinding.inflate(this.layoutInflater)
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setCancelable(false)
        .setView(view.root)
        .setPositiveButton(android.R.string.ok, null)
        .show()
    val confirm = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
    confirm.isEnabled = false

    val scopeImpl = object : ModelLogDialogScope {
        override suspend fun log(line: CharSequence) {
            withContext(Dispatchers.Main) {
                if (view.text.text.isNotEmpty()) view.text.append("\n")
                view.text.append(line)
                view.scroll.post { view.scroll.fullScroll(View.FOCUS_DOWN) }
            }
        }

        override suspend fun progress(current: Int, max: Int) {
            withContext(Dispatchers.Main) {
                view.progressIndicator.isIndeterminate = false
                view.progressIndicator.max = max.coerceAtLeast(1)
                view.progressIndicator.setProgressCompat(current, true)
            }
        }
    }

    try {
        scopeImpl.block()
    } finally {
        withContext(Dispatchers.Main) {
            view.progressIndicator.visibility = View.GONE
            dialog.setCancelable(true)
            confirm.isEnabled = true
        }
    }
}
