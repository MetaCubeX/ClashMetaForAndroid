package com.github.kr328.clash.design.util

import android.content.Context
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.ModelProgressBarConfigure

fun ModelProgressBarConfigure.applyFetchStatus(context: Context, status: FetchStatus) {
    when (status.action) {
        FetchStatus.Action.FetchConfiguration -> {
            text = context.getString(R.string.format_fetching_configuration, status.args[0])
            isIndeterminate = true
        }
        FetchStatus.Action.FetchProviders -> {
            text = context.getString(R.string.format_fetching_provider, status.args[0])
            isIndeterminate = false
            max = status.max
            progress = status.progress
        }
        FetchStatus.Action.Verifying -> {
            text = context.getString(R.string.verifying)
            isIndeterminate = false
            max = status.max
            progress = status.progress
        }
    }
}
