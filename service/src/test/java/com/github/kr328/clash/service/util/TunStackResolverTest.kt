package com.github.kr328.clash.service.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TunStackResolverTest {
    private val auto = TunStackResolver.AUTO

    @Test fun auto_followsSubscriptionStack() {
        for (stack in listOf("gvisor", "system", "mixed", "lwip")) {
            val yaml = "tun:\n  enable: true\n  stack: $stack\n"
            assertEquals(stack, TunStackResolver.resolve(yaml, auto), "Auto must honour declared $stack")
        }
    }

    @Test fun auto_isCaseInsensitive() {
        assertEquals("gvisor", TunStackResolver.resolve("tun:\n  stack: gVisor\n", auto))
    }

    @Test fun auto_missingStack_fallsBackToSystem() {
        assertEquals("system", TunStackResolver.resolve("proxies: []\n", auto))
        assertEquals("system", TunStackResolver.resolve("tun:\n  enable: true\n", auto))
        assertEquals("system", TunStackResolver.resolve(null, auto))
    }

    @Test fun auto_unknownDeclaredStack_fallsBackToSystem() {
        assertEquals("system", TunStackResolver.resolve("tun:\n  stack: nonsense\n", auto))
    }

    @Test fun explicitChoice_overridesSubscription() {
        val subMixed = "tun:\n  stack: mixed\n"
        assertEquals("gvisor", TunStackResolver.resolve(subMixed, "gvisor"))
        assertEquals("system", TunStackResolver.resolve(subMixed, "system"))
        assertEquals("mixed", TunStackResolver.resolve("tun:\n  stack: gvisor\n", "mixed"))
    }

    @Test fun unparseableConfig_underAuto_fallsBack() {
        assertEquals("system", TunStackResolver.resolve(": : not yaml : :", auto))
    }
}
