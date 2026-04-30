package com.github.kr328.clash.common.util

import android.content.Context
import java.net.HttpURLConnection

/** Applies [SubscriptionDeviceHeaders] in addition to any existing request properties. */
object SubscriptionHttpHeaders {

    fun applyTo(connection: HttpURLConnection, context: Context, userAgentOverride: String? = null) {
        SubscriptionRequestHeaders.build(context, userAgentOverride).forEach { (k, v) ->
            connection.setRequestProperty(k, v)
        }
    }
}
