package universe.agentic.clash

import com.github.kr328.clash.design.R

enum class AboutAction { Mail, Url, Copy, JoinGroup, SubPage, None }

data class AboutItem(
    val id: String,
    val labelRes: Int?,
    val value: String,
    val action: AboutAction,
    val target: String? = null,
    val valueRes: Int? = null,
    val qrRes: Int? = null,
    val qrHintRes: Int? = null,
)

data class OpenSourceComponent(
    val name: String,
    val author: String,
    val license: String,
    val url: String,
)

object AboutContent {
    const val DEVELOPER_NAME = "Clarence Henry"
    const val EMAIL = "anyvoid@qq.com"
    const val COOLAPK_URL = "https://www.coolapk.com/u/469700"
    const val WECHAT_NAME = "超越观"
    const val TELEGRAM_URL = "https://t.me/universesoftware"
    const val QQ_CHANNEL = "pd72903491"
    const val REPOSITORY_URL = "https://github.com/TIIEHenry/AgenticClash"
    const val LICENSE_NAME = "GPL-3.0"

    val developer: List<AboutItem> = listOf(
        AboutItem("name", null, DEVELOPER_NAME, AboutAction.None),
        AboutItem("tagline", null, "", AboutAction.None, valueRes = R.string.author_tagline),
        AboutItem("email", R.string.author_email, EMAIL, AboutAction.Mail),
        AboutItem("coolapk", R.string.author_coolapk, COOLAPK_URL, AboutAction.Url),
        AboutItem(
            "wechat",
            R.string.author_wechat,
            WECHAT_NAME,
            AboutAction.Copy,
            qrRes = R.drawable.author_wechat_qr,
            qrHintRes = R.string.author_wechat_qr_hint,
        ),
    )

    val community: List<AboutItem> = listOf(
        AboutItem("telegram", R.string.author_telegram, TELEGRAM_URL, AboutAction.Url),
        AboutItem(
            "qq_channel",
            R.string.author_tencent,
            QQ_CHANNEL,
            AboutAction.Copy,
            qrRes = R.drawable.author_qq_channel_qr,
            qrHintRes = R.string.author_qq_channel_qr_hint,
        ),
    )

    val appInfo: List<AboutItem> = listOf(
        AboutItem("source", R.string.about_open_source, REPOSITORY_URL, AboutAction.Url),
        AboutItem("license", R.string.about_license, LICENSE_NAME, AboutAction.None),
        AboutItem("notices", R.string.about_notices, "", AboutAction.SubPage),
    )

    val components: List<OpenSourceComponent> = listOf(
        OpenSourceComponent(
            "ClashMetaForAndroid",
            "MetaCubeX",
            "GPL-3.0",
            "https://github.com/MetaCubeX/ClashMetaForAndroid",
        ),
        OpenSourceComponent(
            "mihomo",
            "MetaCubeX",
            "GPL-3.0",
            "https://github.com/MetaCubeX/mihomo",
        ),
    ).sortedBy { it.name }

    fun target(item: AboutItem): String = when (item.action) {
        AboutAction.Mail -> "mailto:${item.value}"
        else -> item.target ?: item.value
    }

}
