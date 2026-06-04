package com.github.kr328.clash.core.util

import com.github.kr328.clash.core.model.Traffic
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Traffic is a packed Long: high 32 bits = upload, low 32 bits = download. Each 32-bit field is
 * itself scale-encoded — bits 30..31 are a 1024^type multiplier, bits 0..29 are the value in
 * hundredths of that unit (hence the `/100` in formatting). These are characterization tests:
 * they lock the current formatting behaviour, including its boundary quirks.
 */
class TrafficTest {

    /** Encode one 32-bit field: [type] = 0 B / 1 KiB / 2 MiB / 3 GiB, [hundredths] = value*100. */
    private fun enc(type: Int, hundredths: Long): Long = (type.toLong() shl 30) or hundredths

    private fun traffic(uploadEnc: Long, downloadEnc: Long): Traffic =
        (uploadEnc shl 32) or (downloadEnc and 0xFFFFFFFFL)

    /** Match String.format's locale so assertions hold regardless of the test machine locale. */
    private fun fmt(value: Float, unit: String): String = String.format("%.2f %s", value, unit)

    @Test
    fun download_bytes() {
        assertEquals("500 Bytes", traffic(0, enc(0, 500)).trafficDownload())
    }

    @Test
    fun download_kib_mib_gib() {
        assertEquals(fmt(2.50f, "KiB"), traffic(0, enc(1, 250)).trafficDownload())
        assertEquals(fmt(3.00f, "MiB"), traffic(0, enc(2, 300)).trafficDownload())
        assertEquals(fmt(5.12f, "GiB"), traffic(0, enc(3, 512)).trafficDownload())
    }

    @Test
    fun upload_readsHigh32_downloadReadsLow32() {
        val t = traffic(enc(2, 300), enc(0, 0))
        assertEquals(fmt(3.00f, "MiB"), t.trafficUpload())
        assertEquals("0 Bytes", t.trafficDownload())
    }

    @Test
    fun total_sumsScaledUploadAndDownload() {
        val t = traffic(enc(1, 200), enc(1, 300))
        assertEquals(fmt(2.00f, "KiB"), t.trafficUpload())
        assertEquals(fmt(3.00f, "KiB"), t.trafficDownload())
        // 204800 + 307200 = 512000 bytes -> 5.00 KiB
        assertEquals(fmt(5.00f, "KiB"), t.trafficTotal())
    }

    @Test
    fun zero_isBytes() {
        val t = traffic(0, 0)
        assertEquals("0 Bytes", t.trafficUpload())
        assertEquals("0 Bytes", t.trafficDownload())
        assertEquals("0 Bytes", t.trafficTotal())
    }

    @Test
    fun boundary_exactlyOneUnit_fallsToBytes() {
        // enc(1, 100) == 1.00 KiB == 102400 bytes, but the threshold is strict `> 102400`,
        // so exactly one unit is rendered in Bytes. (Documents an off-by-one boundary quirk.)
        assertEquals("102400 Bytes", traffic(0, enc(1, 100)).trafficDownload())
    }
}
