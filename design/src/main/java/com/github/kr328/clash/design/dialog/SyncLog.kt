package com.github.kr328.clash.design.dialog

import android.content.Context
import android.content.DialogInterface
import android.view.View
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.DialogSyncLogBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

interface ModelLogDialogScope {
    /** Set once the user taps cancel; long-running blocks should stop at the next safe point. */
    val isCancelRequested: Boolean

    suspend fun log(line: CharSequence)
    suspend fun progress(current: Int, max: Int)
}

/** A follow-up run the dialog offers once a run has finished, e.g. retrying the failed items. */
class LogDialogRetry(
    val label: CharSequence,
    val block: suspend ModelLogDialogScope.() -> LogDialogRetry?,
)

/**
 * Shows a dialog with a progress bar and a running log while [block] runs.
 *
 * Unlike [withModelProgressBar] the dialog stays open afterwards, so the user can read
 * the final log; it only becomes dismissible once [block] has finished. While running,
 * a cancel button sets [ModelLogDialogScope.isCancelRequested]; the block decides where
 * to stop. If the block returns a [LogDialogRetry], a button offers to run it in the
 * same dialog, which may in turn offer another retry.
 */
suspend fun Context.withModelLogDialog(
    title: CharSequence,
    block: suspend ModelLogDialogScope.() -> LogDialogRetry?,
) {
    val view = DialogSyncLogBinding.inflate(this.layoutInflater)
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setCancelable(false)
        .setView(view.root)
        .setPositiveButton(android.R.string.ok, null)
        .setNegativeButton(android.R.string.cancel, null)
        .setNeutralButton(" ", null)
        .show()
    val confirm = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
    val cancel = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
    val retry = dialog.getButton(DialogInterface.BUTTON_NEUTRAL)

    // true = retry tapped, false = dialog dismissed
    val choices = Channel<Boolean>(Channel.UNLIMITED)

    // Read by the block on a background thread.
    val cancelRequested = AtomicBoolean(false)

    fun append(line: CharSequence) {
        if (view.text.text.isNotEmpty()) view.text.append("\n")
        view.text.append(line)
        view.scroll.post { view.scroll.fullScroll(View.FOCUS_DOWN) }
    }

    // Buttons set via the builder dismiss the dialog on click; override them so they don't.
    cancel.setOnClickListener {
        cancelRequested.set(true)
        cancel.isEnabled = false
        append(getString(R.string.sync_log_stopping))
    }
    retry.setOnClickListener {
        retry.visibility = View.GONE
        choices.trySend(true)
    }
    dialog.setOnDismissListener { choices.trySend(false) }

    val scopeImpl = object : ModelLogDialogScope {
        override val isCancelRequested: Boolean
            get() = cancelRequested.get()

        override suspend fun log(line: CharSequence) {
            withContext(Dispatchers.Main) { append(line) }
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
        var step: (suspend ModelLogDialogScope.() -> LogDialogRetry?)? = block

        while (step != null) {
            withContext(Dispatchers.Main) {
                cancelRequested.set(false)
                view.progressIndicator.isIndeterminate = true
                view.progressIndicator.visibility = View.VISIBLE
                dialog.setCancelable(false)
                confirm.isEnabled = false
                cancel.isEnabled = true
                cancel.visibility = View.VISIBLE
                retry.visibility = View.GONE
            }

            val next = try {
                scopeImpl.step()
            } finally {
                withContext(Dispatchers.Main) {
                    view.progressIndicator.visibility = View.GONE
                    cancel.visibility = View.GONE
                    dialog.setCancelable(true)
                    confirm.isEnabled = true
                }
            }

            if (next == null) break

            withContext(Dispatchers.Main) {
                retry.text = next.label
                retry.visibility = View.VISIBLE
            }

            step = if (choices.receive()) next.block else null
        }
    } catch (e: CancellationException) {
        dialog.dismiss()
        throw e
    }
}
