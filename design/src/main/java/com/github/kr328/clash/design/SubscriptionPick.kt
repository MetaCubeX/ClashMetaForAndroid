package com.github.kr328.clash.design

/**
 * Another imported profile’s subscription, offered as a source row for proxy-providers merge UI.
 */
data class SubscriptionPick(
    val label: String,
    val rowTitle: String,
    val url: String,
    val intervalSeconds: Long,
)
