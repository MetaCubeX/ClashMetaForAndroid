package com.github.kr328.clash.design.dialog

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.theme.YumeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ModelProgressBarConfigure {
    var isIndeterminate: Boolean
    var text: String?
    var progress: Int
    var max: Int
}

interface ModelProgressBarScope {
    suspend fun configure(block: suspend ModelProgressBarConfigure.() -> Unit)
}

suspend fun Context.withModelProgressBar(block: suspend ModelProgressBarScope.() -> Unit) {
    var composeView: ComposeView? = null

    withContext(Dispatchers.Main) {
        val isIndeterminateState = mutableStateOf(true)
        val textState = mutableStateOf<String?>(null)
        val progressState = mutableStateOf(0)
        val maxState = mutableStateOf(100)

        val configureImpl = object : ModelProgressBarConfigure {
            override var isIndeterminate: Boolean
                get() = isIndeterminateState.value
                set(value) {
                    isIndeterminateState.value = value
                }
            override var text: String?
                get() = textState.value
                set(value) {
                    textState.value = value
                }
            override var progress: Int
                get() = progressState.value
                set(value) {
                    progressState.value = value
                }
            override var max: Int
                get() = maxState.value
                set(value) {
                    maxState.value = value
                }
        }

        val scopeImpl = object : ModelProgressBarScope {
            override suspend fun configure(block: suspend ModelProgressBarConfigure.() -> Unit) {
                withContext(Dispatchers.Main) {
                    configureImpl.block()
                }
            }
        }

        composeView = ComposeView(this@withModelProgressBar).apply {
            setContent {
                YumeTheme {
                    val isIndeterminate by isIndeterminateState
                    val text by textState
                    val progress by progressState
                    val max by maxState

                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {},
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (isIndeterminate) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        progress = { if (max > 0) progress.toFloat() / max.toFloat() else 0f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (text != null) {
                                    Text(
                                        text = text!!,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        val decorView = (this@withModelProgressBar as? android.app.Activity)?.window?.decorView as? ViewGroup
        decorView?.addView(composeView)

        try {
            scopeImpl.block()
        } finally {
            decorView?.removeView(composeView)
        }
    }
}