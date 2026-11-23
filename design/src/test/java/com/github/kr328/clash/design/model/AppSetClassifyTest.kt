package com.github.kr328.clash.design.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSetClassifyTest {
    @Test
    fun browsersStayOutOfSystemAndDomestic() {
        val domestic = setOf("com.tencent.mm", "mark.via", "com.android.browser")
        val browsers = setOf("mark.via", "com.android.chrome", "com.android.browser")
        val installed = listOf(
            "com.android.settings" to true,
            "com.android.browser" to true,
            "com.android.chrome" to false,
            "mark.via" to false,
            "com.tencent.mm" to false,
            "org.telegram.messenger" to false,
        )

        val members = classifyBuiltinAppSets(installed, domestic, browsers)

        assertEquals(setOf("com.android.settings"), members.system)
        assertEquals(setOf("com.tencent.mm"), members.domestic)
        assertEquals(setOf("com.android.browser", "com.android.chrome", "mark.via"), members.browsers)
    }

    @Test
    fun builtinIdsCoverTheThreeCatalogSets() {
        assertEquals(true, AppSetIds.isBuiltin(AppSetIds.SYSTEM))
        assertEquals(true, AppSetIds.isBuiltin(AppSetIds.DOMESTIC))
        assertEquals(true, AppSetIds.isBuiltin(AppSetIds.BROWSERS))
        assertEquals(false, AppSetIds.isBuiltin("custom"))
    }
}
