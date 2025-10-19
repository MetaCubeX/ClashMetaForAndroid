package com.github.kr328.clash.design.util

import android.content.Context
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.R
import com.github.kr328.clash.service.model.Profile

/**
 * 格式化工具类
 * 合并自 util/I18n.kt (格式化部分)
 */

// ========== 类型字符串转换 ==========

fun Profile.Type.toString(context: Context): String {
    return when (this) {
        Profile.Type.File -> context.getString(R.string.file)
        Profile.Type.Url -> context.getString(R.string.url)
        Profile.Type.External -> context.getString(R.string.external)
    }
}

fun Provider.type(context: Context): String {
    val type = when (type) {
        Provider.Type.Proxy -> context.getString(R.string.proxy)
        Provider.Type.Rule -> context.getString(R.string.rule)
    }

    val vehicle = when (vehicleType) {
        Provider.VehicleType.HTTP -> context.getString(R.string.http)
        Provider.VehicleType.File -> context.getString(R.string.file)
        Provider.VehicleType.Inline -> context.getString(R.string.inline)
        Provider.VehicleType.Compatible -> context.getString(R.string.compatible)
    }

    return context.getString(R.string.format_provider_type, type, vehicle)
}

// ========== 数据大小格式化 ==========

/** 字节格式化 */
fun Long.toBytesString(): String {
    return when {
        this > 1024.0 * 1024 * 1024 * 1024 * 1024 * 1024 ->
            String.format("%.2f EiB", (this.toDouble() / 1024 / 1024 / 1024 / 1024 / 1024 / 1024))

        this > 1024.0 * 1024 * 1024 * 1024 * 1024 ->
            String.format("%.2f PiB", (this.toDouble() / 1024 / 1024 / 1024 / 1024 / 1024))

        this > 1024.0 * 1024 * 1024 * 1024 ->
            String.format("%.2f TiB", (this.toDouble() / 1024 / 1024 / 1024 / 1024))

        this > 1024 * 1024 * 1024 ->
            String.format("%.2f GiB", (this.toDouble() / 1024 / 1024 / 1024))

        this > 1024 * 1024 ->
            String.format("%.2f MiB", (this.toDouble() / 1024 / 1024))

        this > 1024 ->
            String.format("%.2f KiB", (this.toDouble() / 1024))

        else ->
            "$this Bytes"
    }
}

// ========== 数值转换 ==========

fun Double.toProgress(): Int {
    return this.toInt()
}

