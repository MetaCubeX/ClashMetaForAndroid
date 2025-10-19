package com.github.kr328.clash.design.util

import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.core.model.TunnelState
import dev.oom_wg.purejoy.mlang.MLang

/**
 * 共享的 SelectionMapping 定义，避免在各个页面重复创建。
 */
object CommonMappings {

    /**
     * 隧道模式映射（带"不修改"选项）
     */
    val tunnelMode = SelectionMapping(
        values = listOf(
            TunnelState.Mode.Direct,
            TunnelState.Mode.Global,
            TunnelState.Mode.Rule,
            TunnelState.Mode.Script,
            null
        ),
        labels = listOf(
            MLang.mode_direct,
            MLang.mode_global,
            MLang.mode_rule,
            MLang.mode_script,
            MLang.tristate_not_modify
        )
    )

    /**
     * 日志级别映射（带"不修改"选项）
     */
    val logLevel = SelectionMapping(
        values = listOf(
            LogMessage.Level.Info,
            LogMessage.Level.Warning,
            LogMessage.Level.Error,
            LogMessage.Level.Debug,
            LogMessage.Level.Silent,
            LogMessage.Level.Unknown,
            null
        ),
        labels = listOf(
            MLang.log_info,
            MLang.log_warning,
            MLang.log_error,
            MLang.log_debug,
            MLang.log_silent,
            MLang.log_unknown,
            MLang.tristate_not_modify
        )
    )
}

