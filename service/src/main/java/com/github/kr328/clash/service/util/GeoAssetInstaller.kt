package com.github.kr328.clash.service.util

import android.content.Context
import com.github.kr328.clash.common.log.Log
import java.io.File
import java.io.FileOutputStream

private val bundledGeoAssets = listOf(
    "geoip.metadb",
    "geoip.dat",
    "geosite.dat",
    "Country.mmdb",
    "ASN.mmdb",
)

fun Context.ensureBundledGeoAssets() {
    val clashDir = filesDir.resolve("clash").apply { mkdirs() }
    val updateDate = packageManager.getPackageInfo(packageName, 0).lastUpdateTime

    for (assetName in bundledGeoAssets) {
        ensureAssetFresh(clashDir, assetName, updateDate)
    }
}

private fun Context.ensureAssetFresh(clashDir: File, assetName: String, updateDate: Long) {
    val target = clashDir.resolve(assetName)
    if (target.exists() && (target.length() <= 0L || target.lastModified() < updateDate)) {
        target.delete()
    }
    if (target.exists()) return

    try {
        FileOutputStream(target).use { output ->
            assets.open(assetName).use { input ->
                input.copyTo(output)
            }
        }
    } catch (e: Exception) {
        Log.w("Asset $assetName not bundled, skipping ($e)")
    }
}
