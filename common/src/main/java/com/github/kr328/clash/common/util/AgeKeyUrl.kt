package com.github.kr328.clash.common.util

/**
 * Subscription links may carry the profile's age decryption key in the URL
 * fragment: `https://panel/sub/TOKEN#AGE-SECRET-KEY-…`. The fragment never
 * leaves the device (HTTP clients strip it before sending), so this is the
 * zero-UX way for an operator to hand the secret key to a user — one link,
 * no manual key entry. On import/edit we split the key out into the profile's
 * ageSecretKey field and keep the clean URL as the source.
 */
object AgeKeyUrl {
    private const val KEY_PREFIX = "AGE-SECRET-KEY-"

    data class Split(val source: String, val ageSecretKey: String?)

    /**
     * @return the source without the key fragment, plus the extracted key —
     *         or the input unchanged when the fragment is absent or is not an
     *         age identity (regular `#anchor` fragments pass through).
     */
    fun split(source: String): Split {
        val hashAt = source.indexOf('#')
        if (hashAt < 0) return Split(source, null)

        val fragment = source.substring(hashAt + 1).trim()
        if (!fragment.startsWith(KEY_PREFIX, ignoreCase = true)) return Split(source, null)

        return Split(source.substring(0, hashAt), fragment.uppercase())
    }
}
