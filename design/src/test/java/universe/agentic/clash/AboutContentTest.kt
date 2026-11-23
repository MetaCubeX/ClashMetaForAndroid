package universe.agentic.clash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutContentTest {
    @Test
    fun developerGroupKeepsTheContractOrder() {
        assertEquals(
            listOf("name", "tagline", "email", "coolapk", "wechat"),
            AboutContent.developer.map { it.id },
        )
        assertEquals(
            listOf(
                "Clarence Henry",
                "",
                "anyvoid@qq.com",
                "https://www.coolapk.com/u/469700",
                "超越观",
            ),
            AboutContent.developer.map { it.value },
        )
    }

    @Test
    fun communityGroupHasNoQqGroupAndKeepsTelegramFirst() {
        assertEquals(
            listOf("telegram", "qq_channel"),
            AboutContent.community.map { it.id },
        )
        assertEquals(
            listOf("https://t.me/universesoftware", "pd72903491"),
            AboutContent.community.map { it.value },
        )
    }

    @Test
    fun appInfoEndsWithTheNoticesSubPage() {
        assertEquals(
            listOf("source", "license", "notices"),
            AboutContent.appInfo.map { it.id },
        )
        assertEquals(AboutAction.SubPage, AboutContent.appInfo.last().action)
        assertEquals("GPL-3.0", AboutContent.appInfo[1].value)
    }

    @Test
    fun mailTargetsUseMailto() {
        val email = AboutContent.developer.first { it.id == "email" }
        assertEquals("mailto:anyvoid@qq.com", AboutContent.target(email))
    }

    @Test
    fun componentsAreSortedAndCarryLicenses() {
        val names = AboutContent.components.map { it.name }
        assertEquals(names.sorted(), names)
        assertTrue(AboutContent.components.all { it.license.isNotBlank() })
        assertTrue(AboutContent.components.all { it.url.startsWith("https://") })
    }

    @Test
    fun componentsOnlyListUpstreamProjects() {
        assertEquals(
            listOf("ClashMetaForAndroid", "mihomo"),
            AboutContent.components.map { it.name },
        )
    }

    @Test
    fun onlyWechatAndQqChannelCarryQrCodes() {
        val qrIds = (AboutContent.developer + AboutContent.community)
            .filter { it.qrRes != null }
            .map { it.id }
        assertEquals(listOf("wechat", "qq_channel"), qrIds)
    }
}
