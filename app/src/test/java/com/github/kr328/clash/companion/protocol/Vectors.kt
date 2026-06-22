package com.github.kr328.clash.companion.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

/**
 * Loads the language-neutral golden vectors from the `clash-companion` git submodule, so the
 * Kotlin implementation is proven byte-for-byte interoperable with the Go reference
 * (PROTOCOL.md §11). Resolves the submodule path by walking up from the test working directory,
 * which differs between Gradle (module dir) and IDE (repo root) runs.
 */
object Vectors {
    private val dir: File by lazy {
        var d: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (d != null) {
            val candidate = File(d, "clash-companion/vectors")
            if (File(candidate, "ids.json").isFile) return@lazy candidate
            d = d.parentFile
        }
        error(
            "clash-companion vectors not found. Initialize the submodule: " +
                "git submodule update --init clash-companion",
        )
    }

    fun text(name: String): String = File(dir, name).readText(Charsets.UTF_8)

    fun json(name: String): JsonElement = Json.parseToJsonElement(text(name))
}
