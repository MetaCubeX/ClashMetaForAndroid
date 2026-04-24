package com.github.kr328.clash.design.model

/**
 * Per-app language preference applied via AppCompatDelegate.setApplicationLocales.
 *
 * `System` clears the app-specific override so the system locale is used.
 * Other entries map to BCP-47 language tags supported by the bundled translations.
 */
enum class AppLanguage(val tag: String) {
    System(""),
    English("en"),
    Russian("ru"),
    Chinese("zh"),
}
