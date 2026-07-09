package com.github.kr328.clash.service.util

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FetchHeadersFileTest {
    private fun dirWith(json: String?): File {
        val dir = Files.createTempDirectory("fhf").toFile()
        if (json != null) File(dir, FetchHeadersFile.FILE_NAME).writeText(json)
        return dir
    }

    @Test
    fun reads_headers_case_insensitively() {
        // The Go writer lowercases keys; consumers use historical mixed-case ones.
        val h = FetchHeadersFile.readFrom(
            dirWith("""{"subscription-userinfo":"upload=1; download=2; total=3; expire=4","content-disposition":"attachment; filename=x.yaml"}"""),
        )!!
        assertEquals("upload=1; download=2; total=3; expire=4", h.get("Subscription-Userinfo"))
        assertEquals("attachment; filename=x.yaml", h.get("Content-Disposition"))
        assertNull(h.get("x-brand-name"))
    }

    @Test
    fun missing_file_returns_null() {
        assertNull(FetchHeadersFile.readFrom(dirWith(null)))
    }

    @Test
    fun malformed_json_returns_null() {
        assertNull(FetchHeadersFile.readFrom(dirWith("not json at all")))
    }
}
