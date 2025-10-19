package com.github.kr328.clash.design.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import com.github.kr328.clash.common.compat.getDrawableCompat
import com.github.kr328.clash.design.R

interface ClickableScope {
    fun focusable(defaultValue: Boolean): Boolean
    fun clickable(defaultValue: Boolean): Boolean
    fun background(): Drawable?
    fun foreground(): Drawable?
}

val Context.selectableItemBackground: Drawable?
    get() = try {
        val resourceId = resolveThemedResourceId(android.R.attr.selectableItemBackground)
        if (resourceId != 0) {
            getDrawableCompat(resourceId)
        } else {
            ContextCompat.getDrawable(this, android.R.drawable.list_selector_background)
        }
    } catch (e: Exception) {
        null
    }

fun Context.resolveClickableAttrs(
    attributeSet: AttributeSet?,
    @AttrRes defaultAttrRes: Int = 0,
    @StyleRes defaultStyleRes: Int = 0,
    block: ClickableScope.() -> Unit,
) {
    try {
        theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.Clickable,
            defaultAttrRes,
            defaultStyleRes
        ).use { typedArray ->
            val impl = object : ClickableScope {
                override fun focusable(defaultValue: Boolean): Boolean {
                    return typedArray.getBoolean(R.styleable.Clickable_android_focusable, defaultValue)
                }

                override fun clickable(defaultValue: Boolean): Boolean {
                    return typedArray.getBoolean(R.styleable.Clickable_android_clickable, defaultValue)
                }

                override fun background(): Drawable? {
                    return try {
                        typedArray.getDrawable(R.styleable.Clickable_android_background)
                    } catch (e: Exception) {
                        null
                    }
                }

                override fun foreground(): Drawable? {
                    return try {
                        // 修复：应该使用 android_foreground 而不是 android_focusable
                        typedArray.getDrawable(android.R.attr.foreground)
                    } catch (e: Exception) {
                        try {
                            typedArray.getDrawable(R.styleable.Clickable_android_focusable)
                        } catch (e2: Exception) {
                            null
                        }
                    }
                }
            }

            impl.apply(block)
        }
    } catch (e: Exception) {
        // 在出现异常时使用默认实现
        val impl = object : ClickableScope {
            override fun focusable(defaultValue: Boolean): Boolean = defaultValue
            override fun clickable(defaultValue: Boolean): Boolean = defaultValue
            override fun background(): Drawable? = null
            override fun foreground(): Drawable? = null
        }
        impl.apply(block)
    }
}

fun Context.resolveThemedColor(@AttrRes resId: Int): Int {
    return try {
        TypedValue().apply {
            if (theme.resolveAttribute(resId, this, true)) {
                data
            } else {
                0
            }
        }.data
    } catch (e: Exception) {
        0
    }
}

fun Context.resolveThemedBoolean(@AttrRes resId: Int): Boolean {
    return try {
        TypedValue().apply {
            theme.resolveAttribute(resId, this, true)
        }.data != 0
    } catch (e: Exception) {
        false
    }
}

fun Context.resolveThemedResourceId(@AttrRes resId: Int): Int {
    return try {
        TypedValue().apply {
            theme.resolveAttribute(resId, this, true)
        }.resourceId
    } catch (e: Exception) {
        0
    }
}

/**
 * 安全获取 Drawable 资源
 */
fun Context.getDrawableSafely(@AttrRes attrId: Int): Drawable? {
    return try {
        val resourceId = resolveThemedResourceId(attrId)
        if (resourceId != 0) {
            getDrawableCompat(resourceId)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * 安全获取颜色资源
 */
fun Context.getColorSafely(@AttrRes attrId: Int, defaultColor: Int = 0): Int {
    return try {
        val color = resolveThemedColor(attrId)
        if (color != 0) color else defaultColor
    } catch (e: Exception) {
        defaultColor
    }
}
