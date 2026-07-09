package com.github.kr328.clash.design.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.Spanned
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DimenRes
import androidx.annotation.StringRes
import com.github.kr328.clash.common.compat.fromHtmlCompat

val Context.layoutInflater: LayoutInflater
    get() = LayoutInflater.from(this)

/**
 * The [Activity] hosting this context, unwrapping any [ContextWrapper] chain — designs may be
 * inflated from a ContextThemeWrapper over their activity (MainDesign under a brand accent)
 * instead of the activity itself.
 */
tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}

val Context.root: ViewGroup?
    get() = hostActivity()?.findViewById(android.R.id.content)

fun Context.getPixels(@DimenRes resId: Int): Int {
    return resources.getDimensionPixelSize(resId)
}

fun Context.getHtml(@StringRes resId: Int): Spanned {
    return fromHtmlCompat(getString(resId))
}
