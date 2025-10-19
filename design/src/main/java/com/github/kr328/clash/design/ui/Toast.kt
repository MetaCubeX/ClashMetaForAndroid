package com.github.kr328.clash.design.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Toast Duration

enum class ToastDuration {
    Short, Long, Indefinite
}

fun ToastDuration.toMillis(): Long {
    return when (this) {
        ToastDuration.Short -> 2000L
        ToastDuration.Long -> 3500L
        ToastDuration.Indefinite -> Long.MAX_VALUE
    }
}

class ToastConfiguration {
    var action: String? = null
    var onActionClick: (() -> Unit)? = null
}

enum class MiuixToastType {
    Info, Success, Error, Default
}

@Composable
fun MiuixToast(
    message: String,
    type: MiuixToastType = MiuixToastType.Default,
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    val backgroundColor = when (type) {
        MiuixToastType.Success -> Color(0xFF4CAF50)
        MiuixToastType.Error -> Color(0xFFF44336)
        MiuixToastType.Info -> Color(0xFF2196F3)
        MiuixToastType.Default -> MiuixTheme.colorScheme.surface
    }

    val contentColor = when (type) {
        MiuixToastType.Success, MiuixToastType.Error, MiuixToastType.Info -> Color.White
        MiuixToastType.Default -> MiuixTheme.colorScheme.onBackground
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun MiuixToastHost(
    toastState: MiuixToastState,
    modifier: Modifier = Modifier
) {
    MiuixToast(
        message = toastState.message,
        type = toastState.type,
        isVisible = toastState.isVisible,
        onDismiss = toastState::hide
    )
}

class MiuixToastState {
    var message by mutableStateOf("")
    var type by mutableStateOf(MiuixToastType.Default)
    var isVisible by mutableStateOf(false)
        private set

    fun show(message: String, type: MiuixToastType = MiuixToastType.Default) {
        this.message = message
        this.type = type
        this.isVisible = true
    }

    fun hide() {
        this.isVisible = false
    }

    suspend fun showWithDelay(
        message: String,
        type: MiuixToastType = MiuixToastType.Default,
        duration: ToastDuration = ToastDuration.Short
    ) {
        show(message, type)
        delay(duration.toMillis())
        hide()
    }
}

@Composable
fun rememberMiuixToastState(): MiuixToastState {
    return remember { MiuixToastState() }
}

