package com.github.kr328.clash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService

/**
 * ExternalControlReceiver - 用于 Tasker 等自动化工具的后台控制接收器
 *
 * 相比 ExternalControlActivity，BroadcastReceiver 完全在后台运行，
 * 不会触发任何界面，适合各种 ROM（包括 Flyme 等国产 ROM）的自动化场景。
 *
 * 使用方法（Tasker）：
 * - 动作类型：Send Intent
 * - Action：com.github.metacubex.clash.meta.action.START_CLASH (或 STOP_CLASH / TOGGLE_CLASH)
 * - Target：Broadcast Receiver
 * - Package：com.github.metacubex.clash.meta
 */
class ExternalControlReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ExternalControlReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到广播: action=${intent.action}")

        when (intent.action) {
            "com.github.metacubex.clash.meta.action.START_CLASH" -> {
                Log.d(TAG, "处理 START_CLASH，当前状态: ${Remote.broadcasts.clashRunning}")
                // 只在未运行时启动
                if (!Remote.broadcasts.clashRunning) {
                    val vpnRequest = context.startClashService()
                    if (vpnRequest != null) {
                        Log.e(TAG, "需要 VPN 权限，请先在应用中手动启动一次")
                        // BroadcastReceiver 无法启动 Activity 请求权限
                        // 用户需要先在应用中手动启动一次授予 VPN 权限
                    } else {
                        Log.d(TAG, "Clash 服务已启动")
                    }
                } else {
                    Log.d(TAG, "Clash 已在运行")
                }
            }

            "com.github.metacubex.clash.meta.action.STOP_CLASH" -> {
                Log.d(TAG, "处理 STOP_CLASH，当前状态: ${Remote.broadcasts.clashRunning}")
                // 只在运行时停止
                if (Remote.broadcasts.clashRunning) {
                    context.stopClashService()
                    Log.d(TAG, "Clash 服务已停止")
                } else {
                    Log.d(TAG, "Clash 未在运行")
                }
            }

            "com.github.metacubex.clash.meta.action.TOGGLE_CLASH" -> {
                Log.d(TAG, "处理 TOGGLE_CLASH，当前状态: ${Remote.broadcasts.clashRunning}")
                // 切换状态
                if (Remote.broadcasts.clashRunning) {
                    context.stopClashService()
                    Log.d(TAG, "Clash 服务已停止")
                } else {
                    val vpnRequest = context.startClashService()
                    if (vpnRequest != null) {
                        Log.e(TAG, "需要 VPN 权限，请先在应用中手动启动一次")
                    } else {
                        Log.d(TAG, "Clash 服务已启动")
                    }
                }
            }
        }
    }
}
