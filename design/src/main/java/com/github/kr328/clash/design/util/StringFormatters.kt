package com.github.kr328.clash.design.util

/**
 * 字符串格式化工具，统一处理各种数量/状态的显示逻辑，避免硬编码。
 *
 * 改进后的版本支持 Context 参数以获取本地化字符串资源。
 */
object StringFormatters {

    private object DefaultTexts {
        // 数量格式化后缀
        const val PORTS_SUFFIX = "个端口"
        const val DOMAINS_SUFFIX = "个域名"
        const val ADDRESSES_SUFFIX = "个地址"
        const val SERVERS_SUFFIX = "个服务器"
        const val SEGMENTS_SUFFIX = "个网段"
        const val POLICIES_SUFFIX = "个策略"
        const val MAPPINGS_SUFFIX = "个映射"
        const val ITEMS_SUFFIX = "项"

        // 状态文本
        const val NOT_CONFIGURED = "未配置"
        const val NOT_SET = "未设置"
        const val SECRET_HIDDEN = "••••••••"
        const val LOADING = "加载中…"
        const val LOADING_HINT = "如果长时间无反应请刷新"
        const val NO_DATA = "暂无数据"
        const val EMPTY_LIST = "列表为空"

        // 协议类型
        const val PROTOCOL_HTTP = "HTTP"
        const val PROTOCOL_TLS = "TLS"
        const val PROTOCOL_QUIC = "QUIC"
    }

    /**
     * 格式化数量字符串
     */
    private fun formatCount(count: Int, suffix: String): String {
        return when {
            count <= 0 -> "0$suffix"
            count == 1 -> "1$suffix"
            else -> "$count$suffix"
        }
    }

    fun formatPortsCount(count: Int): String = formatCount(count, DefaultTexts.PORTS_SUFFIX)
    fun formatDomainsCount(count: Int): String = formatCount(count, DefaultTexts.DOMAINS_SUFFIX)
    fun formatAddressesCount(count: Int): String = formatCount(count, DefaultTexts.ADDRESSES_SUFFIX)
    fun formatServersCount(count: Int): String = formatCount(count, DefaultTexts.SERVERS_SUFFIX)
    fun formatSegmentsCount(count: Int): String = formatCount(count, DefaultTexts.SEGMENTS_SUFFIX)
    fun formatPoliciesCount(count: Int): String = formatCount(count, DefaultTexts.POLICIES_SUFFIX)
    fun formatMappingsCount(count: Int): String = formatCount(count, DefaultTexts.MAPPINGS_SUFFIX)
    fun formatItemsCount(count: Int): String = formatCount(count, DefaultTexts.ITEMS_SUFFIX)

    object StatusText {
        val notConfigured: String get() = DefaultTexts.NOT_CONFIGURED
        val notSet: String get() = DefaultTexts.NOT_SET
        val secretHidden: String get() = DefaultTexts.SECRET_HIDDEN
        val loading: String get() = DefaultTexts.LOADING
        val loadingHint: String get() = DefaultTexts.LOADING_HINT
        val noData: String get() = DefaultTexts.NO_DATA
        val emptyList: String get() = DefaultTexts.EMPTY_LIST
    }

    object Protocol {
        val http: String get() = DefaultTexts.PROTOCOL_HTTP
        val tls: String get() = DefaultTexts.PROTOCOL_TLS
        val quic: String get() = DefaultTexts.PROTOCOL_QUIC

        fun fromString(protocol: String?): String {
            return when (protocol?.lowercase()) {
                "http" -> http
                "https" -> "HTTPS"
                "tls" -> tls
                "quic" -> quic
                else -> protocol?.uppercase() ?: "UNKNOWN"
            }
        }
    }

    fun formatSniffPortsTitle(protocol: String): String = "Sniff ${Protocol.fromString(protocol)} Ports"
    fun formatSniffOverrideTitle(protocol: String): String =
        "Sniff ${Protocol.fromString(protocol)} Override Destination"

    /**
     * 通用格式化方法，支持自定义后缀
     */
    fun formatCustomCount(count: Int, suffix: String): String = formatCount(count, suffix)

    /**
     * 安全的数值格式化，防止空值
     */
    fun formatCountSafely(count: Int?, suffix: String): String {
        return formatCount(count ?: 0, suffix)
    }
}

fun <T> Collection<T>?.toCountSummary(
    formatter: (Int) -> String,
    emptyText: String = StringFormatters.StatusText.notConfigured
): String {
    return this?.takeIf { it.isNotEmpty() }
        ?.let { formatter(it.size) }
        ?: emptyText
}

fun Int?.toPortSummary(
    emptyText: String = StringFormatters.StatusText.notSet
): String = this?.toString() ?: emptyText

fun String?.toStringSummary(
    emptyText: String = StringFormatters.StatusText.notSet
): String = this?.ifBlank { null } ?: emptyText

fun String?.toSecretSummary(
    emptyText: String = StringFormatters.StatusText.notSet
): String = if (this.isNullOrBlank()) emptyText else StringFormatters.StatusText.secretHidden

fun List<*>?.toPortsSummary(): String =
    this.toCountSummary(StringFormatters::formatPortsCount)

fun List<*>?.toDomainsSummary(): String =
    this.toCountSummary(StringFormatters::formatDomainsCount)

fun List<*>?.toAddressesSummary(): String =
    this.toCountSummary(StringFormatters::formatAddressesCount)

fun List<*>?.toServersSummary(): String =
    this.toCountSummary(StringFormatters::formatServersCount)

fun List<*>?.toSegmentsSummary(): String =
    this.toCountSummary(StringFormatters::formatSegmentsCount)

fun List<*>?.toPoliciesSummary(): String =
    this.toCountSummary(StringFormatters::formatPoliciesCount)

fun Map<*, *>?.toPoliciesSummary(): String =
    this?.takeIf { it.isNotEmpty() }
        ?.let { StringFormatters.formatPoliciesCount(it.size) }
        ?: StringFormatters.StatusText.notConfigured

fun List<*>?.toMappingsSummary(): String =
    this.toCountSummary(StringFormatters::formatMappingsCount)

fun Map<*, *>?.toMappingsSummary(): String =
    this?.takeIf { it.isNotEmpty() }
        ?.let { StringFormatters.formatMappingsCount(it.size) }
        ?: StringFormatters.StatusText.notConfigured

fun List<*>?.toItemsSummary(): String =
    this.toCountSummary(StringFormatters::formatItemsCount)

fun Int?.formatCountSafely(formatter: (Int) -> String): String {
    return formatter(this ?: 0)
}

fun String?.toSafeSummary(
    emptyText: String = StringFormatters.StatusText.notSet,
    maxLength: Int? = null
): String {
    val result = this?.ifBlank { null } ?: emptyText
    return if (maxLength != null && result.length > maxLength) {
        "${result.take(maxLength)}..."
    } else {
        result
    }
}

