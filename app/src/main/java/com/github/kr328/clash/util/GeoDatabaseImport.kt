package com.github.kr328.clash.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import com.github.kr328.clash.design.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class GeoDatabaseImportKind {
    GeoIp,
    GeoSite,
    CountryMmdb,
    AsnMmdb,
}

enum class GeoDatabaseImportResult {
    Success,
    BadExtension,
    Failed,
}

private val validDatabaseExtensions = listOf(
    ".metadb", ".db", ".dat", ".mmdb",
)

suspend fun Context.importGeoDatabaseFromUri(
    uri: Uri?,
    kind: GeoDatabaseImportKind,
): GeoDatabaseImportResult {
    val cursor: Cursor? = uri?.let {
        contentResolver.query(it, null, null, null, null, null)
    }
    cursor?.use {
        if (it.moveToFirst()) {
            val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val displayName: String =
                if (columnIndex != -1) it.getString(columnIndex) else ""
            val ext = "." + displayName.substringAfterLast(".")

            if (!validDatabaseExtensions.contains(ext)) {
                return GeoDatabaseImportResult.BadExtension
            }
            val outputFileName = when (kind) {
                GeoDatabaseImportKind.GeoIp -> "geoip$ext"
                GeoDatabaseImportKind.GeoSite -> "geosite$ext"
                GeoDatabaseImportKind.CountryMmdb -> "country$ext"
                GeoDatabaseImportKind.AsnMmdb -> "ASN$ext"
            }

            withContext(Dispatchers.IO) {
                val outputFile = File(clashDir, outputFileName)
                contentResolver.openInputStream(uri).use { ins ->
                    FileOutputStream(outputFile).use { outs ->
                        ins?.copyTo(outs)
                    }
                }
            }
            Toast.makeText(
                this,
                getString(R.string.geofile_imported, displayName),
                Toast.LENGTH_LONG,
            ).show()
            return GeoDatabaseImportResult.Success
        }
    }
    Toast.makeText(this, R.string.geofile_import_failed, Toast.LENGTH_LONG).show()
    return GeoDatabaseImportResult.Failed
}

fun geoDatabaseImportAcceptedExtensionsLabel(): String =
    validDatabaseExtensions.joinToString("/")
