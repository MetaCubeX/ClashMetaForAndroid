package com.github.kr328.clash.design.util

import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.ui.ToastDuration

suspend fun Design<*>.showExceptionToast(message: CharSequence) {
    showToast(message, ToastDuration.Long)
}

suspend fun Design<*>.showExceptionToast(exception: Exception) {
    showExceptionToast(exception.message ?: "Unknown")
}