package com.github.kr328.clash.agent

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.github.kr328.clash.R
import com.github.kr328.clash.design.Design
import kotlin.math.max

class AgentScreenDesign(context: Context) : Design<Unit>(context) {
    override val root: View = LayoutInflater.from(context).inflate(R.layout.activity_agent, null, false)

    init {
        val header = root.findViewById<View>(R.id.agent_header)
        val composer = root.findViewById<View>(R.id.agent_composer)
        val density = context.resources.displayMetrics.density
        val headerHeight = context.resources.getDimensionPixelSize(R.dimen.agent_header_height)
        val composerBottom = (10 * density).toInt()

        // The application is edge-to-edge. Consume both system-bar and IME insets
        // directly here so the toolbar never sits behind a cutout/status bar and the
        // composer follows the keyboard instead of being covered by it.
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            header.updateLayoutParams { height = headerHeight + bars.top }
            header.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            composer.updatePadding(
                left = bars.left + (12 * density).toInt(),
                right = bars.right + (8 * density).toInt(),
                bottom = composerBottom + max(bars.bottom, ime.bottom),
            )

            windowInsets
        }
        root.post { ViewCompat.requestApplyInsets(root) }
    }
}
