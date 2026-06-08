package com.github.kr328.clash.service.util

import kotlin.test.Test
import kotlin.test.assertEquals

class MergeEngineVerdictTest {
    @Test fun ok_when_merged_valid() {
        assertEquals(MergeEngineVerdict.Ok, MergeEngineVerdict.classify(null, null))
        assertEquals(MergeEngineVerdict.Ok, MergeEngineVerdict.classify("fetched bad", null))
    }

    @Test fun merge_introduced_when_fetched_ok_but_merged_bad() {
        assertEquals(MergeEngineVerdict.MergeIntroduced, MergeEngineVerdict.classify(null, "boom"))
    }

    @Test fun preexisting_when_both_bad() {
        assertEquals(MergeEngineVerdict.PreexistingBroken, MergeEngineVerdict.classify("already bad", "boom"))
    }
}
