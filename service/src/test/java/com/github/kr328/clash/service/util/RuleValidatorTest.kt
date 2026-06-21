package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RuleValidatorTest {
    private fun rule(type: String, value: String, policy: String, raw: String = "", id: String = type) =
        RuleItem(id = id, raw = raw, type = type, value = value, policy = policy)

    @Test
    fun opaqueRules_withEmptyValueAndPolicy_pass() {
        // Regression: a single AND/OR/NOT/SUB-RULE rule from the subscription
        // (value + policy empty, payload in `raw`) used to fail the WHOLE save
        // with "Rule value is empty for type AND" — blocking the user from
        // adding an unrelated DOMAIN-SUFFIX rule.
        val state = RuleState(
            rules = listOf(
                rule("AND", "", "", raw = "AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT"),
                rule("OR", "", "", raw = "OR,((GEOIP,CN),(DOMAIN-SUFFIX,cn)),DIRECT"),
                rule("SUB-RULE", "", "", raw = "SUB-RULE,((DOMAIN,foo.com)),GLOBAL"),
                rule("DOMAIN-SUFFIX", "google.com", "DIRECT", raw = "DOMAIN-SUFFIX,google.com,DIRECT"),
            ),
        )
        // must not throw
        RuleValidator.validate(state)
    }

    @Test
    fun opaqueRule_withBlankRaw_isRejected() {
        val state = RuleState(rules = listOf(rule("AND", "", "", raw = "")))
        val e = assertFailsWith<IllegalArgumentException> { RuleValidator.validate(state) }
        assertEquals("Rule line is empty for type AND", e.message)
    }

    @Test
    fun regularRule_withEmptyValue_stillRejected() {
        val state = RuleState(rules = listOf(rule("DOMAIN", "", "DIRECT", raw = "DOMAIN,,DIRECT")))
        val e = assertFailsWith<IllegalArgumentException> { RuleValidator.validate(state) }
        assertEquals("Rule value is empty for type DOMAIN", e.message)
    }
}
