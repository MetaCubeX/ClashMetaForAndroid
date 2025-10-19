package com.github.kr328.clash.design.constants

/**
 * 代理模块相关常量
 *
 * 集中管理所有与代理功能相关的配置参数，避免魔法数字
 */
object ProxyConstants {

    object DelayTest {
        const val CACHE_VALID_TIME = 30_000L
        const val TEST_IN_PROGRESS_TIMEOUT = 60_000L
        const val TEST_CLEANUP_TIMEOUT = 90_000L
        const val TEST_CHECK_INTERVAL = 300L
    }

    object Concurrency {
        const val SMALL_GROUP_SIZE = 50
        const val MEDIUM_GROUP_SIZE = 100
        const val LARGE_GROUP_SIZE = 200
        const val SMALL_GROUP_CONCURRENCY = 8
        const val MEDIUM_GROUP_CONCURRENCY = 12
        const val LARGE_GROUP_CONCURRENCY = 16
        const val EXTRA_LARGE_GROUP_CONCURRENCY = 20

        fun getConcurrency(proxyCount: Int): Int = when {
            proxyCount <= SMALL_GROUP_SIZE -> SMALL_GROUP_CONCURRENCY
            proxyCount <= MEDIUM_GROUP_SIZE -> MEDIUM_GROUP_CONCURRENCY
            proxyCount <= LARGE_GROUP_SIZE -> LARGE_GROUP_CONCURRENCY
            else -> EXTRA_LARGE_GROUP_CONCURRENCY
        }
    }

    object WaitTime {
        const val SMALL_GROUP_MAX_WAIT = 8_000L
        const val MEDIUM_GROUP_MAX_WAIT = 12_000L
        const val LARGE_GROUP_MAX_WAIT = 16_000L
        const val EXTRA_LARGE_GROUP_MAX_WAIT = 20_000L

        fun getMaxWaitTime(proxyCount: Int): Long = when {
            proxyCount <= Concurrency.SMALL_GROUP_SIZE -> SMALL_GROUP_MAX_WAIT
            proxyCount <= Concurrency.MEDIUM_GROUP_SIZE -> MEDIUM_GROUP_MAX_WAIT
            proxyCount <= Concurrency.LARGE_GROUP_SIZE -> LARGE_GROUP_MAX_WAIT
            else -> EXTRA_LARGE_GROUP_MAX_WAIT
        }
    }

    object UI {
        const val SORT_DEBOUNCE_DELAY = 200L
        const val LAYOUT_TYPE_SINGLE = 0
        const val LAYOUT_TYPE_DOUBLE = 1
        const val LAYOUT_TYPE_COUNT = 2
    }
}
