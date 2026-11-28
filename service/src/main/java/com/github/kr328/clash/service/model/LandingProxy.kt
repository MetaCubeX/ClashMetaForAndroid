package com.github.kr328.clash.service.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class LandingProxyType(val wireName: String) {
    Http("http"),
    Https("https"),
    Socks5("socks5"),
}

/**
 * Explicit landing (exit) proxy, chained behind [frontGroup]:
 * device -> front group's selected node -> landing proxy -> target.
 *
 * Serialized as the JSON consumed by the core (`native/config/landing.go`).
 */
@Serializable
data class LandingProxy(
    @SerialName("type") val type: String,
    @SerialName("server") val server: String,
    @SerialName("port") val port: Int,
    @SerialName("username") val username: String = "",
    @SerialName("password") val password: String = "",
    @SerialName("frontGroup") val frontGroup: String = "",
    @SerialName("skipCertVerify") val skipCertVerify: Boolean = false,
    @SerialName("sni") val sni: String = "",
    @SerialName("udp") val udp: Boolean = false,
)
