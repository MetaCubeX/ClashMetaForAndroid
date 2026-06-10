package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.RoutingHubDesign
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class RoutingHubActivity : BaseActivity<RoutingHubDesign>() {
    override suspend fun main() {
        val design = RoutingHubDesign(this)

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive { }
                design.requests.onReceive {
                    when (it) {
                        RoutingHubDesign.Request.OpenRules ->
                            launch { openRulesHub() }
                        RoutingHubDesign.Request.OpenPerAppRouting ->
                            startActivity(AccessControlActivity::class.intent)
                        RoutingHubDesign.Request.OpenProxyChain ->
                            startActivity(ProxyChainActivity::class.intent)
                    }
                }
            }
        }
    }

    private suspend fun openRulesHub() {
        val uuid = withProfile { queryActive()?.takeIf { it.imported }?.uuid }
        val hubIntent = (uuid?.let { RulesHubActivity::class.intent.setUUID(it) }
            ?: RulesHubActivity::class.intent)
        startActivity(hubIntent)
    }
}
