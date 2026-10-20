package com.github.kr328.clash.design.preference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NullableTextAdapterPortTest {
    @Test
    fun portToKeepsNullEmptyAndParsedAndDoesNotZeroGarbage() {
        assertNull(NullableTextAdapter.Port.to(null))
        assertEquals(0 as Any?, NullableTextAdapter.Port.to("") as Any?)
        assertEquals(80 as Any?, NullableTextAdapter.Port.to("80") as Any?)
        assertNotEquals(0 as Any?, NullableTextAdapter.Port.to("abc") as Any?)
    }
}
