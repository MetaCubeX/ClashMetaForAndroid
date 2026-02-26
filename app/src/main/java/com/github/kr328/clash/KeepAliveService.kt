package com.github.kr328.clash

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * KeepAliveService - 保持应用活跃的前台服务
 *
 * 用途：确保 ExternalControlReceiver 能可靠地接收来自 Tasker 等自动化工具的广播。
 * 通过运行一个轻量级的前台服务，防止应用进入深度休眠状态（CACHED_EMPTY），
 * 从而让 BroadcastReceiver 能够接收到第三方应用发送的广播。
 *
 * 特性：
 * - 使用最低优先级的通知（IMPORTANCE_MIN），不会打扰用户
 * - 几乎不消耗系统资源，仅用于保持进程优先级
 * - 在应用启动时自动启动，确保自动化功能始终可用
 */
class KeepAliveService : Service() {
    companion object {
        private const val TAG = "KeepAliveService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "clash_keepalive"
        private const val CHANNEL_NAME = "后台自动化"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KeepAliveService 已创建")

        // 创建通知渠道（Android 8.0+）
        createNotificationChannel()

        // 启动前台服务
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        Log.d(TAG, "前台服务已启动，应用将保持活跃以接收自动化广播")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: 服务正在运行")
        // START_STICKY: 如果服务被系统杀死，会自动重启
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "KeepAliveService 已销毁")
    }

    override fun onBind(intent: Intent?): IBinder? {
        // 这是一个纯前台服务，不提供绑定
        return null
    }

    /**
     * 创建通知渠道（Android 8.0+ 必需）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // 使用 IMPORTANCE_MIN，通知会被最小化，不会发出声音和震动
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "保持应用活跃，确保 Tasker 等自动化工具能正常控制 Clash"
                setShowBadge(false) // 不显示角标
                enableLights(false) // 不闪烁指示灯
                enableVibration(false) // 不震动
                setSound(null, null) // 不发出声音
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "通知渠道已创建: $CHANNEL_ID")
        }
    }

    /**
     * 创建前台服务通知
     */
    private fun createNotification(): Notification {
        // 点击通知时打开主界面
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Clash 服务")
            .setContentText("运行中")
            .setSmallIcon(R.mipmap.ic_launcher) // 使用应用启动器图标
            .setOngoing(true) // 不可滑动清除
            .setPriority(NotificationCompat.PRIORITY_MIN) // 最低优先级
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        // 设置点击事件
        builder.setContentIntent(pendingIntent)

        return builder.build()
    }
}
