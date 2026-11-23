package com.github.kr328.clash

import android.os.Build
import android.os.Bundle
import com.github.kr328.clash.core.model.ConfigurationOverride

internal const val PERSIST_OVERRIDE_STATE = "persist_override"

internal fun Bundle.readPersistOverride(key: String): ConfigurationOverride? {
    classLoader = ConfigurationOverride::class.java.classLoader
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, ConfigurationOverride::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key)
    }
}
