package com.github.kr328.clash.core.bridge

import android.content.Context
import com.github.kr328.clash.common.model.CoreMode
import com.github.kr328.clash.common.store.CoreStore

internal object CoreLoader {
    fun load(context: Context): CoreMode {
        return when (val mode = CoreStore(context).currentMode) {
            CoreMode.Meta -> {
                System.loadLibrary("bridge_meta")
                mode
            }

            CoreMode.Ninja -> {
                System.loadLibrary("bridge")
                mode
            }
        }
    }
}
