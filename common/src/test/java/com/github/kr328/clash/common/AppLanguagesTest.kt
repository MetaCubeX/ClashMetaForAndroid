package com.github.kr328.clash.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppLanguagesTest {
    @Test
    fun unknownOrBlankTagsFollowTheSystem() {
        assertEquals(AppLanguages.FOLLOW_SYSTEM, AppLanguages.canonicalize(null))
        assertEquals(AppLanguages.FOLLOW_SYSTEM, AppLanguages.canonicalize(""))
        assertEquals(AppLanguages.FOLLOW_SYSTEM, AppLanguages.canonicalize("  "))
        assertEquals(AppLanguages.FOLLOW_SYSTEM, AppLanguages.canonicalize("zh-CN"))
        assertEquals(AppLanguages.FOLLOW_SYSTEM, AppLanguages.canonicalize("en-US"))
    }

    @Test
    fun supportedTagsRoundTripInLocaleConfigOrder() {
        assertEquals(
            localeConfigTags(),
            AppLanguages.supported.map { it.tag },
        )
        AppLanguages.supported.forEach { option ->
            assertEquals(option.tag, AppLanguages.canonicalize(" ${option.tag} "))
        }
    }

    @Test
    fun nativeNamesArePresentAndDistinct() {
        val names = AppLanguages.supported.map { it.nativeName }
        assertTrue(names.all { it.isNotBlank() })
        assertEquals(names.size, names.toSet().size)
    }

    private fun localeConfigTags(): List<String> {
        val file = localeConfigFile()
        return Regex("""android:name="([^"]+)"""")
            .findAll(file.readText())
            .map { it.groupValues[1] }
            .toList()
    }

    private fun localeConfigFile(): File {
        val userDir = checkNotNull(System.getProperty("user.dir"))
        var dir = File(userDir)
        repeat(4) {
            val candidate = File(dir, "app/src/main/res/xml/locales_config.xml")
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return candidate
        }
        return File("app/src/main/res/xml/locales_config.xml")
    }
}
