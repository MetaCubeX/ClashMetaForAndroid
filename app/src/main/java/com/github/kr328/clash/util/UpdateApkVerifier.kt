package com.github.kr328.clash.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

object UpdateApkVerifier {
    private const val TRUSTED_SCHEME = "https"
    private const val TRUSTED_HOST = "github.com"
    private const val TRUSTED_OWNER = "Nemu-x"
    private const val TRUSTED_REPO = "ClashFest"

    fun isTrustedDownloadUrl(url: String?): Boolean {
        val uri = trustedGitHubUri(url) ?: return false

        val segments = uri.path.orEmpty()
            .split('/')
            .filter { it.isNotBlank() }
        if (segments.size != 6) return false
        return segments[0].equals(TRUSTED_OWNER, ignoreCase = true) &&
            segments[1].equals(TRUSTED_REPO, ignoreCase = true) &&
            segments[2].equals("releases", ignoreCase = true) &&
            segments[3].equals("download", ignoreCase = true) &&
            segments.last().lowercase(Locale.ROOT).endsWith(".apk")
    }

    fun isTrustedReleasePageUrl(url: String?): Boolean {
        val uri = trustedGitHubUri(url) ?: return false
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        return segments.size == 5 &&
            segments[0].equals(TRUSTED_OWNER, ignoreCase = true) &&
            segments[1].equals(TRUSTED_REPO, ignoreCase = true) &&
            segments[2].equals("releases", ignoreCase = true) &&
            segments[3].equals("tag", ignoreCase = true) &&
            segments[4].isNotBlank()
    }

    private fun trustedGitHubUri(url: String?): URI? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (!uri.scheme.equals(TRUSTED_SCHEME, ignoreCase = true)) return null
        if (!uri.host.equals(TRUSTED_HOST, ignoreCase = true)) return null
        if (uri.rawUserInfo != null || uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null) return null
        return uri
    }

    fun isTrustedDownloadedApk(context: Context, localUri: String?): Boolean {
        if (localUri.isNullOrBlank()) return false
        val apkPath = Uri.parse(localUri).path ?: return false
        val pm = context.packageManager

        val archiveInfo = getArchivePackageInfo(pm, apkPath) ?: return false
        if (archiveInfo.packageName != context.packageName) return false

        val installedInfo = getInstalledPackageInfo(pm, context.packageName) ?: return false
        if (versionCode(archiveInfo) <= versionCode(installedInfo)) return false

        val installed = signaturesDigest(installedInfo)
        val archive = signaturesDigest(archiveInfo)
        return installed.isNotEmpty() && installed == archive
    }

    @Suppress("DEPRECATION")
    private fun getArchivePackageInfo(pm: PackageManager, apkPath: String): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                apkPath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES)
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackageInfo(pm: PackageManager, packageName: String): PackageInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                )
            } else {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    }

    @Suppress("DEPRECATION")
    private fun signaturesDigest(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            val values = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            values?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }

        return signatures.mapTo(linkedSetOf()) {
            val digest = MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
            digest.joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }
        }
    }
}
