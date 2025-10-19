package com.github.kr328.clash.design.util

import android.content.Context
import com.github.kr328.clash.common.compat.preferredLocale
import com.github.kr328.clash.design.R
import dev.oom_wg.purejoy.mlang.MLang
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 日期时间工具类
 * 合并自 util/I18n.kt (日期部分) 和 util/Interval.kt
 */

private object DateTimeConstants {
    const val DATE_DATE_ONLY = "yyyy-MM-dd"
    const val DATE_TIME_ONLY = "HH:mm:ss.SSS"
    const val DATE_ALL = "$DATE_DATE_ONLY $DATE_TIME_ONLY"
    const val DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"

    val TIME_UNITS = TimeUnit.values().associateWith { unit ->
        when (unit) {
            TimeUnit.DAYS -> 24L * 60L * 60L * 1000L
            TimeUnit.HOURS -> 60L * 60L * 1000L
            TimeUnit.MINUTES -> 60L * 1000L
            TimeUnit.SECONDS -> 1000L
            else -> 1L
        }
    }
}

private object DateFormatterCache {
    private val cache = ConcurrentHashMap<String, SimpleDateFormat>()

    fun getFormatter(pattern: String, locale: Locale): SimpleDateFormat {
        val key = "$pattern-${locale.toString()}"
        return cache.getOrPut(key) {
            SimpleDateFormat(pattern, locale)
        }
    }

    fun clearCache() {
        cache.clear()
    }
}

@JvmOverloads
fun Date.format(
    context: Context?,
    includeDate: Boolean = true,
    includeTime: Boolean = true,
): String {
    if (context == null) return ""

    val locale = context.resources.configuration.preferredLocale

    return when {
        includeDate && includeTime ->
            DateFormatterCache.getFormatter(DateTimeConstants.DATE_ALL, locale).format(this)

        includeDate ->
            DateFormatterCache.getFormatter(DateTimeConstants.DATE_DATE_ONLY, locale).format(this)

        includeTime ->
            DateFormatterCache.getFormatter(DateTimeConstants.DATE_TIME_ONLY, locale).format(this)

        else -> ""
    }
}

/**
 * 时间戳转日期字符串
 */
fun Long.toDateStr(): String {
    return if (this <= 0) {
        "--"
    } else {
        DateFormatterCache.getFormatter(DateTimeConstants.DEFAULT_DATE_FORMAT, Locale.getDefault())
            .format(Date(this))
    }
}

/**
 * 相对时间格式化
 */
fun Long.toRelativeTimeString(): String {
    if (this <= 0) return "--"

    val now = System.currentTimeMillis()
    val diff = now - this

    if (diff < DateTimeConstants.TIME_UNITS[TimeUnit.MINUTES]!!) {
        return "刚刚"
    }

    val hours = DateTimeConstants.TIME_UNITS[TimeUnit.HOURS]!!
    val days = DateTimeConstants.TIME_UNITS[TimeUnit.DAYS]!!

    return when {
        diff < hours -> String.format(MLang.time_minutes_ago, diff / DateTimeConstants.TIME_UNITS[TimeUnit.MINUTES]!!)
        diff < days -> String.format(MLang.time_hours_ago, diff / hours)
        else -> String.format(MLang.time_days_ago, diff / days)
    }
}

/**
 * 经过时间间隔字符串（从 Interval.kt）
 */
fun Long.elapsedIntervalString(context: Context?): String {
    if (context == null) return "--"

    val days = TimeUnit.MILLISECONDS.toDays(this)
    val hours = TimeUnit.MILLISECONDS.toHours(this)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this)

    return when {
        days > 0 -> context.getString(R.string.format_days_ago, days)
        hours > 0 -> context.getString(R.string.format_hours_ago, hours)
        minutes > 0 -> context.getString(R.string.format_minutes_ago, minutes)
        else -> context.getString(R.string.recently)
    }
}

/**
 * 清除日期格式化器缓存（用于内存管理）
 */
fun clearDateTimeFormatterCache() {
    DateFormatterCache.clearCache()
}

