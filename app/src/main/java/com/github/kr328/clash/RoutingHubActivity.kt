package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.RoutingHubDesign
import kotlinx.coroutines.isActive
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
                            startActivity(RuleSnippetActivity::class.intent)
                        RoutingHubDesign.Request.OpenEffectiveRules ->
                            startActivity(EffectiveRulesActivity::class.intent)
                        RoutingHubDesign.Request.OpenPerAppRouting ->
                            startActivity(AccessControlActivity::class.intent)
                        RoutingHubDesign.Request.OpenProxyChain ->
                            startActivity(ProxyChainActivity::class.intent)
                    }
                }
            }
        }
    }
}
