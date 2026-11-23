package com.github.kr328.clash

import android.os.Bundle
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.ProxyDesign

class ProxyActivity : BaseActivity<ProxyDesign>() {
    private var restoredGroup: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        restoredGroup = savedInstanceState?.getString(STATE_GROUP)
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        design?.currentGroupName?.let { outState.putString(STATE_GROUP, it) }
    }

    override suspend fun main() {
        var shell: ProxyShell? = null
        shell = ProxyShell(
            host = this,
            embedded = false,
            fallbackGroups = listOfNotNull(
                restoredGroup?.takeIf { it.isNotEmpty() },
                intent.getStringExtra(EXTRA_PROXY_GROUP)?.takeIf { it.isNotEmpty() },
            ),
            onMounted = { setContentDesign(it) },
            onActivityReplace = {
                val next = ProxyActivity::class.intent
                shell?.currentGroup?.let { next.putExtra(EXTRA_PROXY_GROUP, it) }
                startActivity(next)
                finish()
            },
        )
        shell.run()
    }

    companion object {
        const val EXTRA_PROXY_GROUP = "proxy_group"
        private const val STATE_GROUP = "proxy_shown_group"
    }
}
