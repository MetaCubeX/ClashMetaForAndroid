package com.github.kr328.clash.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateApkVerifierTest {
    @Test
    fun acceptsOnlyClashFestGitHubReleaseApkUrls() {
        assertTrue(
            UpdateApkVerifier.isTrustedDownloadUrl(
                "https://github.com/Nemu-x/ClashFest/releases/download/v1.2.3/clashfest-alpha-universal.apk",
            ),
        )
        assertFalse(
            UpdateApkVerifier.isTrustedDownloadUrl(
                "http://github.com/Nemu-x/ClashFest/releases/download/v1.2.3/clashfest.apk",
            ),
        )
        assertFalse(
            UpdateApkVerifier.isTrustedDownloadUrl(
                "https://evil.example/Nemu-x/ClashFest/releases/download/v1.2.3/clashfest.apk",
            ),
        )
        assertFalse(
            UpdateApkVerifier.isTrustedDownloadUrl(
                "https://github.com/Other/ClashFest/releases/download/v1.2.3/clashfest.apk",
            ),
        )
        assertFalse(
            UpdateApkVerifier.isTrustedDownloadUrl(
                "https://github.com/Nemu-x/ClashFest/releases/download/v1.2.3/checksum.txt",
            ),
        )
    }
}
