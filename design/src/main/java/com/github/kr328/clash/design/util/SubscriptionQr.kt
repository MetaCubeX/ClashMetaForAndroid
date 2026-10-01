package com.github.kr328.clash.design.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.getSystemService
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.DialogSubscriptionQrBinding
import com.github.kr328.clash.design.ui.ToastDuration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun isShareableSubscriptionUrl(source: String): Boolean {
    return ValidatorHttpUrl(source.trim())
}

fun Design<*>.showSubscriptionQr(name: String, url: String) {
    val content = url.trim()
    if (!isShareableSubscriptionUrl(content))
        return

    launch(Dispatchers.Default) {
        val size = (context.resources.displayMetrics.density * 280)
            .toInt()
            .coerceIn(480, 1080)
        val bitmap = try {
            encodeSubscriptionQr(content, size)
        } catch (_: WriterException) {
            withContext(Dispatchers.Main) {
                showToast(R.string.subscription_qr_too_long, ToastDuration.Long)
            }
            return@launch
        }

        withContext(Dispatchers.Main) {
            presentSubscriptionQr(name, content, bitmap)
        }
    }
}

private fun Design<*>.presentSubscriptionQr(name: String, url: String, bitmap: Bitmap) {
    val binding = DialogSubscriptionQrBinding
        .inflate(context.layoutInflater, context.root, false)

    binding.qrView.setImageBitmap(bitmap)
    binding.urlView.text = url

    val title = name.ifBlank { context.getString(R.string.share_subscription_qr) }
    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setView(binding.root)
        .setPositiveButton(R.string.ok) { _, _ -> }
        .create()

    binding.copyView.setOnClickListener {
        val data = ClipData.newPlainText(title, url)
        context.getSystemService<ClipboardManager>()?.setPrimaryClip(data)
        launch { showToast(R.string.copied, ToastDuration.Short) }
    }

    binding.shareView.setOnClickListener {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_link)))
    }

    dialog.show()
}

private fun encodeSubscriptionQr(contents: String, size: Int): Bitmap {
    val levels = listOf(ErrorCorrectionLevel.M, ErrorCorrectionLevel.L)
    var error: WriterException? = null

    for (level in levels) {
        try {
            return encodeSubscriptionQr(contents, size, level)
        } catch (e: WriterException) {
            error = e
        }
    }

    throw error ?: WriterException("QR encode failed")
}

private fun encodeSubscriptionQr(
    contents: String,
    size: Int,
    level: ErrorCorrectionLevel,
): Bitmap {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to level,
    )
    val matrix = QRCodeWriter().encode(contents, BarcodeFormat.QR_CODE, size, size, hints)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)

    for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
            pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
    }

    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}
