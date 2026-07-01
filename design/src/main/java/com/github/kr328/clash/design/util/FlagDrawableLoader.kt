package com.github.kr328.clash.design.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import com.caverock.androidsvg.SVG

/**
 * Loads square (1x1) country-flag SVGs bundled in `assets/flags/<iso>.svg`
 * and renders them as bitmaps sized for the target view.
 *
 * Use [loadBitmap] + [android.widget.ImageView.setImageBitmap] — not Drawable —
 * so ImageView does not re-scale via BitmapDrawable density (that caused centerCrop
 * to zoom into the flag center on high-DPI screens).
 *
 * Why bitmap, not SVG-Picture: circular clips need a raster backing on some devices.
 */
object FlagDrawableLoader {

    private val cache = LruCache<String, Bitmap>(64)
    private val missing = HashSet<String>()

    /** @return Bitmap rendered from `assets/flags/<code.lowercase>.svg`, or null when absent. */
    fun loadBitmap(context: Context, code: String?, sizePx: Int): Bitmap? {
        if (code.isNullOrBlank() || sizePx <= 0) return null
        val key = "${code.lowercase()}@$sizePx"
        cache.get(key)?.let { return it }
        if (key in missing) return null

        val assetName = "flags/${code.lowercase()}.svg"
        val bitmap = try {
            context.assets.open(assetName).use { input ->
                val svg = SVG.getFromInputStream(input)
                svg.documentWidth.takeIf { it > 0f }?.let { /* ok */ }
                val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                svg.setDocumentWidth(sizePx.toFloat())
                svg.setDocumentHeight(sizePx.toFloat())
                svg.renderToCanvas(canvas)
                bmp
            }
        } catch (_: Exception) {
            missing.add(key)
            return null
        }
        cache.put(key, bitmap)
        return bitmap
    }
}
