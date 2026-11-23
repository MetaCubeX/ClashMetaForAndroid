package com.github.kr328.clash

import android.content.Context

internal object RawPackageCatalog {
    private val packageName = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
    private val cache = HashMap<Int, Set<String>>()

    fun load(context: Context, resId: Int): Set<String> {
        synchronized(cache) {
            cache[resId]?.let { return it }
            val loaded = read(context, resId)
            cache[resId] = loaded
            return loaded
        }
    }

    private fun read(context: Context, resId: Int): Set<String> {
        return context.resources.openRawResource(resId).bufferedReader().use { reader ->
            reader.lineSequence().mapNotNull { line ->
                val pkg = line.substringBefore('#').trim()
                pkg.takeIf { packageName.matches(it) }
            }.toSet()
        }
    }
}

/**
 * Package names of apps commonly distributed on Chinese app stores.
 * The built-in set is dynamic: only names that are installed on this device are members.
 */
internal object DomesticAppCatalog {
    fun packages(context: Context): Set<String> =
        RawPackageCatalog.load(context, R.raw.domestic_packages)
}

/**
 * Web browsers from Chinese stores and elsewhere.
 * Installed matches belong to the browser set, not the system or domestic set.
 */
internal object BrowserAppCatalog {
    fun packages(context: Context): Set<String> =
        RawPackageCatalog.load(context, R.raw.browser_packages)
}
