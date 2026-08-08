package com.github.kr328.clash.design.component

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.ProxyState
import kotlin.math.absoluteValue
import kotlin.math.max

class ProxyViewState(
    val config: ProxyViewConfig,
    val proxy: Proxy,
    private val parent: ProxyState,
    private val link: ProxyState?
) {
    val paint = Paint()
    val rect = Rect()
    val path = Path()

    var title: String = ""
    var subtitle: String = ""
    var delayText: String = ""

    /**
     * Protocol shown as a badge on the subtitle row. The proxy's type used to be
     * rendered as the subtitle text itself, so it is stripped from the subtitle
     * below — the badge replaces it rather than duplicating it.
     */
    val typeLabel: String = typeLabelOf(proxy.type)

    /** Only set for multi-hop dialer-proxy chains, which are the exception. */
    val chainLabel: String =
        if (!proxy.isGroup && proxy.chain.size > 1) config.context.getString(R.string.chain_badge) else ""

    val isSelected: Boolean
        get() = selected
    var background: Int = config.unselectedBackground
    var controls: Int = config.unselectedControl

    private var delay: Int = 0
    private var selected: Boolean = false
    private var parentNow: String = ""
    private var linkNow: String? = null

    private var lastFrameTime = System.currentTimeMillis()

    fun update(snap: Boolean): Boolean {
        val frameTime = System.currentTimeMillis()
        var invalidate = false

        if (proxy.isGroup) {
            title = proxy.name

            if (link == null) {
                // The type badge already carries this, so the row stays empty.
                subtitle = ""
            } else {
                if (linkNow !== link.now) {
                    linkNow = link.now

                    // Was "Selector(节点名)". The type moved to the badge, so the
                    // row now gives the selection its own full width.
                    subtitle = link.now.ifEmpty { "*" }
                }
            }
        } else {
            title = proxy.title
            subtitle = stripType(proxy.subtitle, proxy.type)
        }

        if (delay != proxy.delay) {
            delay = proxy.delay
            delayText = if (proxy.delay in 0..Short.MAX_VALUE) proxy.delay.toString() else ""
        }

        if (parentNow !== parent.now) {
            parentNow = parent.now
            selected = proxy.name == parent.now
        }

        controls = if (selected) config.selectedControl else config.unselectedControl

        if (snap) {
            background = if (selected) config.selectedBackground else config.unselectedBackground
        } else {
            val target = if (selected) config.selectedBackground else config.unselectedBackground

            if (background != target) {
                val sa = Color.alpha(background)
                val sr = Color.red(background)
                val sg = Color.green(background)
                val sb = Color.blue(background)

                val ta = Color.alpha(target)
                val tr = Color.red(target)
                val tg = Color.green(target)
                val tb = Color.blue(target)

                val da = ta - sa
                val dr = tr - sr
                val dg = tg - sg
                val db = tb - sb

                val max = max(
                    da.absoluteValue,
                    max(
                        dr.absoluteValue,
                        max(
                            dg.absoluteValue,
                            db.absoluteValue
                        )
                    )
                )

                val frameOffset = frameTime - lastFrameTime

                val colorOffset = (frameOffset / max.toFloat().coerceAtLeast(0.001f))
                    .coerceIn(0.0f, 1.0f)

                background = if (colorOffset > 0.999f) {
                    target
                } else {
                    Color.argb(
                        (sa + da * colorOffset).toInt(),
                        (sr + dr * colorOffset).toInt(),
                        (sg + dg * colorOffset).toInt(),
                        (sb + db * colorOffset).toInt()
                    )
                }

                invalidate = true
            }
        }

        lastFrameTime = frameTime

        return invalidate
    }

    private companion object {
        /**
         * The core reports types verbatim ("Shadowsocks", "Hysteria2"). A badge has
         * room for roughly eight glyphs, so the long well-known protocols get their
         * conventional short form and everything else is simply upper-cased.
         */
        fun typeLabelOf(type: String): String = when (type.lowercase()) {
            "" -> ""
            "shadowsocks" -> "SS"
            "shadowsocksr" -> "SSR"
            "hysteria" -> "HY"
            "hysteria2" -> "HY2"
            "wireguard" -> "WG"
            "loadbalance" -> "LB"
            "urltest" -> "URLTEST"
            else -> type.uppercase().take(10)
        }

        /**
         * The core sets the subtitle to the type unless a ui-subtitle-pattern
         * matched part of the node name, in which case it holds that fragment
         * instead (the providers screen joins them with " · "). Only the type is
         * dropped here; a name fragment is real information and stays.
         */
        fun stripType(subtitle: String, type: String): String = when {
            type.isEmpty() -> subtitle
            subtitle.equals(type, ignoreCase = true) -> ""
            subtitle.startsWith("$type · ", ignoreCase = true) -> subtitle.substring(type.length + 3)
            else -> subtitle
        }
    }
}