package universe.agentic.device.clash

import java.security.SecureRandom

fun newMcpToken(): String {
    val bytes = ByteArray(24)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}
