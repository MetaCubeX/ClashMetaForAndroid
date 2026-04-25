package com.github.kr328.clash.service.model

/**
 * Defines how aggressively ClashFest hardens the runtime configuration against
 * the well-known SOCKS5/HTTP/Mixed listener leak (a.k.a. VLESS-SOCKS5
 * vulnerability) and direct access to the TUN interface from other apps.
 *
 * - [Strict] — default on Android 10+. All local proxy listeners are forcibly
 *   disabled (port = 0); only the TUN tunnel is active. Other apps on the
 *   device cannot reach 127.0.0.1:7891 etc.
 * - [Compat] — listeners are kept open but bound to 127.0.0.1 and gated by a
 *   randomly generated session credential. Useful for tools that legitimately
 *   need a local SOCKS proxy and can authenticate.
 * - [Off] — no extra hardening. Behaves like upstream Clash.Meta. Not
 *   recommended.
 */
enum class ProxyHardeningMode {
    Strict,
    Compat,
    Off,
}
