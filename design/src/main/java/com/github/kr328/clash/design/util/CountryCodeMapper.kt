package com.github.kr328.clash.design.util

/**
 * 国家代码映射工具
 *
 * 从代理节点名称中提取国家/地区代码
 */
object CountryCodeMapper {

    private const val UNKNOWN_COUNTRY = "xx"
    private const val FLAG_BASE_URL = "https://hatscripts.github.io/circle-flags/flags/"

    /**
     * 优化后的国家关键字映射表
     * 按使用频率排序，优先匹配常见地区
     */
    private val countryMappings = listOf(
        // 高频地区 - 香港、台湾、日本、韩国、新加坡、美国
        CountryEntry("hk", listOf("香港", "HK", "Hong Kong", "hongkong")),
        CountryEntry("tw", listOf("台湾", "TW", "Taiwan", "taiwan")),
        CountryEntry("jp", listOf("日本", "JP", "Japan", "japan")),
        CountryEntry("kr", listOf("韩国", "KR", "Korea", "korea", "首尔")),
        CountryEntry("sg", listOf("新加坡", "SG", "Singapore", "singapore")),
        CountryEntry("us", listOf("美国", "US", "USA", "United States", "america")),

        // 欧洲地区
        CountryEntry("gb", listOf("英国", "UK", "Britain", "United Kingdom")),
        CountryEntry("de", listOf("德国", "DE", "Germany", "germany")),
        CountryEntry("fr", listOf("法国", "FR", "France", "france")),
        CountryEntry("nl", listOf("荷兰", "NL", "Netherlands", "netherlands")),
        CountryEntry("ch", listOf("瑞士", "CH", "Switzerland", "switzerland")),
        CountryEntry("se", listOf("瑞典", "SE", "Sweden", "sweden")),
        CountryEntry("it", listOf("意大利", "IT", "Italy", "italy")),
        CountryEntry("es", listOf("西班牙", "ES", "Spain", "spain")),
        CountryEntry("pl", listOf("波兰", "PL", "Poland", "poland")),
        CountryEntry("ru", listOf("俄罗斯", "RU", "Russia", "russia")),

        // 美洲地区
        CountryEntry("ca", listOf("加拿大", "CA", "Canada", "canada")),
        CountryEntry("au", listOf("澳大利亚", "AU", "Australia", "australia")),
        CountryEntry("br", listOf("巴西", "BR", "Brazil", "brazil")),
        CountryEntry("mx", listOf("墨西哥", "MX", "Mexico", "mexico")),
        CountryEntry("ar", listOf("阿根廷", "AR", "Argentina", "argentina")),

        // 亚太地区
        CountryEntry("in", listOf("印度", "IN", "India", "india")),
        CountryEntry("th", listOf("泰国", "TH", "Thailand", "thailand")),
        CountryEntry("vn", listOf("越南", "VN", "Vietnam", "vietnam")),
        CountryEntry("ph", listOf("菲律宾", "PH", "Philippines", "philippines")),
        CountryEntry("my", listOf("马来西亚", "MY", "Malaysia", "malaysia")),
        CountryEntry("id", listOf("印尼", "ID", "Indonesia", "indonesia")),

        // 中东地区
        CountryEntry("tr", listOf("土耳其", "TR", "Turkey", "turkey")),
        CountryEntry("ae", listOf("阿联酋", "AE", "UAE", "Dubai", "dubai")),
        CountryEntry("il", listOf("以色列", "IL", "Israel", "israel")),

        // 非洲地区
        CountryEntry("za", listOf("南非", "ZA", "South Africa"))
    )

    // 缓存已解析的结果
    private val extractionCache = mutableMapOf<String, String>()

    private data class CountryEntry(
        val code: String,
        val keywords: List<String>
    )

    /**
     * 从文本中提取国家/地区代码
     *
     * @param text 待提取的文本（通常是代理节点名称）
     * @return 国家/地区代码（小写），如果无法识别则返回 UNKNOWN_COUNTRY
     */
    fun extractCountryCode(text: String?): String {
        if (text.isNullOrBlank()) return UNKNOWN_COUNTRY

        // 检查缓存
        extractionCache[text.lowercase()]?.let { return it }

        val lowerText = text.lowercase()

        // 按优先级查找匹配的国家代码
        for (entry in countryMappings) {
            for (keyword in entry.keywords) {
                if (lowerText.contains(keyword.lowercase())) {
                    val result = entry.code
                    extractionCache[text.lowercase()] = result
                    return result
                }
            }
        }

        // 未找到匹配，缓存结果
        extractionCache[text.lowercase()] = UNKNOWN_COUNTRY
        return UNKNOWN_COUNTRY
    }

    /**
     * 获取国旗 SVG URL
     *
     * @param countryCode 国家/地区代码
     * @return 国旗 SVG 的 URL
     */
    fun getFlagUrl(countryCode: String?): String {
        val code = if (countryCode.isNullOrBlank()) UNKNOWN_COUNTRY else countryCode.lowercase()
        return "${FLAG_BASE_URL}${code}.svg"
    }

    /**
     * 从文本中提取国旗 URL
     *
     * @param text 待提取的文本
     * @return 国旗 SVG 的 URL
     */
    fun extractFlagUrl(text: String?): String {
        val code = extractCountryCode(text)
        return getFlagUrl(code)
    }

    /**
     * 清除缓存（用于内存管理）
     */
    fun clearCache() {
        extractionCache.clear()
    }

    /**
     * 获取支持的国家/地区代码列表
     */
    fun getSupportedCountryCodes(): Set<String> {
        return countryMappings.map { it.code }.toSet()
    }

    /**
     * 验证国家代码是否有效
     */
    fun isValidCountryCode(code: String?): Boolean {
        if (code.isNullOrBlank()) return false
        return getSupportedCountryCodes().contains(code.lowercase())
    }
}


