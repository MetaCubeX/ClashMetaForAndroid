package com.github.kr328.clash.design.branding

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import java.io.File

/**
 * Decodes the cached brand-logo bitmap from its `<filesDir>/brand/<sha256>`
 * path and assigns it to an [ImageView]. Result is cached process-wide so
 * the same logo isn't re-decoded for every rebind.
 *
 * Falls back to clearing the image when the path is null / unreadable.
 */
object BrandLogoBinder {

    private val cache = LruCache<String, Drawable>(8)

    fun bind(view: ImageView, path: String?) {
        if (path.isNullOrBlank()) {
            view.setImageDrawable(null)
            return
        }
        cache.get(path)?.let {
            view.setImageDrawable(it)
            return
        }
        val file = File(path)
        if (!file.isFile) {
            view.setImageDrawable(null)
            return
        }
        val drawable = runCatching {
            val bitmap = BitmapFactory.decodeFile(path) ?: return@runCatching null
            BitmapDrawable(view.resources, bitmap)
        }.getOrNull()
        if (drawable == null) {
            view.setImageDrawable(null)
            return
        }
        cache.put(path, drawable)
        view.setImageDrawable(drawable)
    }

    fun invalidate() = cache.evictAll()
}
