package com.github.kr328.clash.design.dialog

import android.content.Context
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.github.kr328.clash.design.theme.YumeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume


typealias LegacyValidator = (String) -> Boolean

suspend fun Context.requestModelTextInput(
    initial: String,
    title: CharSequence,
    hint: CharSequence? = null,
    error: CharSequence? = null,
    validator: LegacyValidator = { true },
): String {
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var composeView: ComposeView? = null

            composeView = ComposeView(this@requestModelTextInput).apply {
                setContent {
                    YumeTheme {
                        val dialogState = DialogState(true)

                        val unifiedValidator = Validator<String> { value ->
                            if (validator(value)) null else error?.toString()
                        }

                        UnifiedInputDialog(
                            title = title.toString(),
                            state = dialogState,
                            initialValue = initial,
                            label = hint?.toString() ?: "",
                            validator = unifiedValidator,
                            onConfirm = { result ->
                                if (!continuation.isCompleted) {
                                    continuation.resume(result)
                                }
                                (composeView?.parent as? ViewGroup)?.removeView(composeView)
                            },
                            onDismiss = {
                                if (!continuation.isCompleted) {
                                    continuation.resume(initial)
                                }
                                (composeView?.parent as? ViewGroup)?.removeView(composeView)
                            }
                        )
                    }
                }
            }

            val decorView = (this@requestModelTextInput as? android.app.Activity)
                ?.window?.decorView as? ViewGroup

            decorView?.addView(composeView)

            continuation.invokeOnCancellation {
                (composeView?.parent as? ViewGroup)?.removeView(composeView)
            }
        }
    }
}

suspend fun Context.requestModelTextInput(
    initial: String?,
    title: CharSequence,
    reset: CharSequence?,
    hint: CharSequence? = null,
    error: CharSequence? = null,
    validator: LegacyValidator = { true },
): String? {
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var composeView: ComposeView? = null

            composeView = ComposeView(this@requestModelTextInput).apply {
                setContent {
                    YumeTheme {
                        val dialogState = DialogState(true)

                        val unifiedValidator = Validator<String> { value ->
                            if (value.isEmpty() && reset != null) {
                                null
                            } else if (validator(value)) {
                                null
                            } else {
                                error?.toString()
                            }
                        }

                        UnifiedInputDialog(
                            title = title.toString(),
                            state = dialogState,
                            initialValue = initial ?: "",
                            label = hint?.toString() ?: "",
                            validator = unifiedValidator,
                            onConfirm = { result ->
                                if (!continuation.isCompleted) {
                                    if (result.isEmpty() && reset != null) {
                                        continuation.resume(null)
                                    } else {
                                        continuation.resume(result)
                                    }
                                }
                                (composeView?.parent as? ViewGroup)?.removeView(composeView)
                            },
                            onDismiss = {
                                if (!continuation.isCompleted) {
                                    continuation.resume(initial)
                                }
                                (composeView?.parent as? ViewGroup)?.removeView(composeView)
                            },
                            cancelText = if (reset != null) reset.toString() else dev.oom_wg.purejoy.mlang.MLang.action_cancel
                        )
                    }
                }
            }

            val decorView = (this@requestModelTextInput as? android.app.Activity)
                ?.window?.decorView as? ViewGroup

            decorView?.addView(composeView)

            continuation.invokeOnCancellation {
                (composeView?.parent as? ViewGroup)?.removeView(composeView)
            }
        }
    }
}
