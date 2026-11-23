package com.github.kr328.clash

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.drawable.toBitmap
import com.github.kr328.clash.common.compat.versionCodeCompat
import com.github.kr328.clash.design.AboutDesign
import com.github.kr328.clash.design.ui.ToastDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.kr328.clash.design.R as DesignR

class AboutActivity : BaseActivity<AboutDesign>() {
    override suspend fun main() {
        val design = AboutDesign(this, versionName(), appIcon())
        setContentDesign(design)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!design.back()) finish()
            }
        })

        while (isActive) {
            when (val request = design.requests.receive()) {
                is AboutDesign.Request.Open -> open(design, request.url)
                is AboutDesign.Request.Copy -> copy(design, request.text)
            }
        }
    }

    private suspend fun versionName(): String {
        return withContext(Dispatchers.IO) {
            val info = packageManager.getPackageInfo(packageName, 0)
            val version = info.versionName.orEmpty()
                .takeWhile { it.isDigit() || it == '.' }
                .trimEnd('.')
                .ifEmpty { info.versionName.orEmpty() }
            "v$version (Build ${info.versionCodeCompat})"
        }
    }

    private suspend fun appIcon(): Bitmap {
        return withContext(Dispatchers.IO) {
            applicationInfo.loadIcon(packageManager)
                .toBitmap(width = 192, height = 192)
        }
    }

    private suspend fun open(design: AboutDesign, url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            offerCopy(design, url)
        } catch (e: Exception) {
            offerCopy(design, url)
        }
    }

    private suspend fun offerCopy(design: AboutDesign, url: String) {
        val mail = url.startsWith("mailto:")
        val text = url.removePrefix("mailto:")
        val message = if (mail) DesignR.string.author_mail_failed else DesignR.string.author_browser_failed
        design.showToast(message, ToastDuration.Long) {
            setAction(DesignR.string.author_copy) {
                this@AboutActivity.launch { copy(design, text) }
            }
        }
    }

    private suspend fun copy(design: AboutDesign, text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("author", text))
            design.showToast(DesignR.string.copied, ToastDuration.Short)
        } catch (e: Exception) {
            design.showToast(DesignR.string.author_copy_failed, ToastDuration.Short)
        }
    }
}
