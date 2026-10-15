package com.github.kr328.clash

import android.content.Context

/**
 * Package names of apps commonly distributed on Chinese app stores.
 * The built-in set is dynamic: only names that are installed on this device are members.
 */
internal object DomesticAppCatalog {
    private val packageName = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")

    @Volatile
    private var cached: Set<String>? = null

    fun packages(context: Context): Set<String> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: load(context).also { cached = it }
        }
    }

    private fun load(context: Context): Set<String> {
        return context.resources.openRawResource(R.raw.domestic_packages).bufferedReader().use { reader ->
            reader.lineSequence().mapNotNull { line ->
                val pkg = line.substringBefore('#').trim()
                pkg.takeIf { packageName.matches(it) }
            }.toSet()
        }
    }
}
