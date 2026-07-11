package com.github.kr328.clash.service.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RequestHistoryCsvTest {
    @Test
    fun spreadsheetFormulaPrefixesAreNeutralized() {
        for (value in listOf("=WEBSERVICE(\"https://evil\")", "+1", "-1", "@SUM(A1)", "  =1+1")) {
            val encoded = csvForExport(value)
            assertEquals('\'', encoded.trimStart('"').first())
        }
    }

    @Test
    fun ordinaryAndQuotedValuesKeepCsvSemantics() {
        assertEquals("proxy-name", csvForExport("proxy-name"))
        assertEquals("\"a,b\"", csvForExport("a,b"))
        assertEquals("\"a\"\"b\"", csvForExport("a\"b"))
    }
}
