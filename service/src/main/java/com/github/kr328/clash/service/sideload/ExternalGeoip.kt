package com.github.kr328.clash.service.sideload

import android.content.Context
import android.content.pm.PackageManager
import com.github.kr328.clash.common.constants.Metadata
import com.github.kr328.clash.common.log.Log
import java.io.IOException

@Throws(IOException::class)
fun readGeoipDatabaseFrom(context: Context, packageName: String): ByteArray? {
    return try {
        @Suppress("DEPRECATION")
        val metadata = context.packageManager.getApplicationInfo(
            packageName,
            PackageManager.GET_META_DATA
        ).metaData
        val fileName = metadata?.getString(Metadata.GEOIP_FILE_NAME) ?: return null

        context.createPackageContext(packageName, 0).assets.open(fileName).use {
            it.readBytes()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w("Sideload geoip: $packageName not found", e)

        null
    }
}
