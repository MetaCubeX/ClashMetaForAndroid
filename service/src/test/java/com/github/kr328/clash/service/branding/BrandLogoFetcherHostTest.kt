package com.github.kr328.clash.service.branding

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrandLogoFetcherHostTest {
    @Test
    fun rejectsPrivateAndLoopbackLiteralHosts() {
        assertFalse(BrandLogoFetcher.isAllowedLiteralHost("127.0.0.1"))
        assertFalse(BrandLogoFetcher.isAllowedLiteralHost("2130706433"))
        assertFalse(BrandLogoFetcher.isAllowedLiteralHost("127.1"))
        assertFalse(BrandLogoFetcher.isAllowedLiteralHost("0177.0.0.1"))
        assertFalse(BrandLogoFetcher.isAllowedLiteralHost("192.168.1.1"))
        assertFalse(BrandLogoFetcher.isAllowedLiteralHost("169.254.1.1"))
        assertFalse(BrandLogoFetcher.isAllowedLiteralHost("::1"))
        assertFalse(BrandLogoFetcher.isAllowedLiteralHost("fd00::1"))
    }

    @Test
    fun allowsDnsNamesAndPublicLiteralHosts() {
        assertTrue(BrandLogoFetcher.isAllowedLiteralHost("cdn.example.com"))
        assertTrue(BrandLogoFetcher.isAllowedLiteralHost("1.1"))
        assertTrue(BrandLogoFetcher.isAllowedLiteralHost("1.1.1.1"))
        assertTrue(BrandLogoFetcher.isAllowedLiteralHost("2606:4700:4700::1111"))
    }
}
