package com.github.kr328.clash.util

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** Network behaviour of [HttpTextFetcher] against a local MockWebServer (never a live host). */
class HttpTextFetcherTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun returnsUtf8BodyOn200() {
        server.enqueue(MockResponse().setBody("héllo wörld"))

        val out = HttpTextFetcher.fetchUtf8(server.url("/cfg").toString())

        assertEquals("héllo wörld", out)
    }

    @Test
    fun emptyBodyOn200ReturnsEmptyString() {
        server.enqueue(MockResponse().setBody(""))

        assertEquals("", HttpTextFetcher.fetchUtf8(server.url("/empty").toString()))
    }

    @Test
    fun sendsCustomRequestHeaders() {
        server.enqueue(MockResponse().setBody("ok"))

        HttpTextFetcher.fetchUtf8(
            server.url("/h").toString(),
            headers = mapOf("X-Test" to "abc", "Authorization" to "Bearer t"),
        )

        val req = server.takeRequest()
        assertEquals("abc", req.getHeader("X-Test"))
        assertEquals("Bearer t", req.getHeader("Authorization"))
    }

    @Test
    fun throwsOnHttpErrorStatus() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))

        assertThrows(Exception::class.java) {
            HttpTextFetcher.fetchUtf8(server.url("/missing").toString())
        }
    }

    @Test
    fun throwsOnServerError() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertThrows(Exception::class.java) {
            HttpTextFetcher.fetchUtf8(server.url("/boom").toString())
        }
    }

    @Test
    fun throwsOnConnectionDrop() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertThrows(Exception::class.java) {
            HttpTextFetcher.fetchUtf8(
                server.url("/drop").toString(),
                connectTimeoutMs = 1000,
                readTimeoutMs = 1000,
            )
        }
    }
}
