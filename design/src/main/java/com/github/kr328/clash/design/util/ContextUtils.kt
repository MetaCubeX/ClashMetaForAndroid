package com.github.kr328.clash.design.util

import android.app.Activity
import android.content.Context
import android.text.Spanned
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DimenRes
import androidx.annotation.StringRes
import com.github.kr328.clash.common.compat.fromHtmlCompat

val Context.layoutInflater: LayoutInflater
    get() = LayoutInflater.from(this)

val Context.root: ViewGroup?
    get() {
        return when (this) {
            is Activity -> {
                findViewById(android.R.id.content)
            }

            else -> {
                null
            }
        }
    }

fun Context.getPixels(@DimenRes resId: Int): Int {
    return resources.getDimensionPixelSize(resId)
}

fun Context.getHtml(@StringRes resId: Int): Spanned {
    return fromHtmlCompat(getString(resId))
}

/**
 * 安全地结束 Activity
 *
 * 如果当前 Context 是 Activity，则调用 finish()
 */
fun Context.finishActivity() {
    (this as? Activity)?.finish()
}
