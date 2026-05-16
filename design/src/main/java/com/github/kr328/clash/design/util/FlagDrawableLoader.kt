package com.github.kr328.clash.design.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.caverock.androidsvg.SVG

/**
 * Loads square (1x1) country-flag SVGs bundled in `assets/flags/<iso>.svg`
 * and renders them as bitmap-backed Drawables sized for the proxy node row.
 *
 * Why bitmap, not SVG-Picture: ShapeableImageView clips to the configured
 * shape only when the contained drawable is bitmap-backed. Picture-backed
 * SVG drawables draw outside the clip on some devices.
 *
 * Cache keyed by ISO code; rendering happens at most once per country per
 * process. 64 entries is enough for the busiest subscriptions (most
 * picker views show <30 distinct countries).
 */
object FlagDrawableLoader {

    private const val MISSING_SENTINEL_KEY = "__missing__"
    private val cache = LruCache<String, Drawable>(64)
    private val missing = HashSet<String>()

    /** @return Drawable rendered from `assets/flags/<code.lowercase>.svg`, or null when absent. */
    fun load(context: Context, code: String?, sizePx: Int): Drawable? {
        if (code.isNullOrBlank() || sizePx <= 0) return null
        val key = "${code.lowercase()}@$sizePx"
        cache.get(key)?.let { return it }
        if (key in missing) return null

        val assetName = "flags/${code.lowercase()}.svg"
        val drawable = try {
            context.assets.open(assetName).use { input ->
                val svg = SVG.getFromInputStream(input)
                svg.documentWidth.takeIf { it > 0f }?.let { /* ok */ }
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                svg.setDocumentWidth(sizePx.toFloat())
                svg.setDocumentHeight(sizePx.toFloat())
                svg.renderToCanvas(canvas)
                BitmapDrawable(context.resources, bitmap)
            }
        } catch (_: Exception) {
            missing.add(key)
            return null
        }
        cache.put(key, drawable)
        return drawable
    }
}
