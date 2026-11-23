package universe.agentic.device.clash

import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.startForegroundCompat
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.service.BaseService
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.store.ServiceStore

class McpService : BaseService() {
    private var bridge: McpBridge? = null
    private var http: McpHttpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val store = ServiceStore(this)
        val port = store.mcpPort.takeIf { it in 1..65535 } ?: McpProtocol.DEFAULT_PORT
        val endpoint = "http://127.0.0.1:$port${McpProtocol.PATH}"
        val channel = "mcp_status_channel"
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(channel, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getText(R.string.mcp_channel))
                .build()
        )
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_logo_service)
            .setOngoing(true)
            .setColor(getColorCompat(R.color.color_clash))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentTitle(getText(R.string.mcp_notification))
            .setContentText(endpoint)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    R.id.nf_mcp,
                    Intent().setComponent(Components.MAIN_ACTIVITY)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
                )
            )
            .build()
        startForegroundCompat(R.id.nf_mcp, notification)
        if (!store.mcpEnabled) {
            stopForeground(true)
            stopSelf()
            return
        }
        var token = store.mcpToken
        if (token.isBlank()) {
            token = newMcpToken()
            store.mcpToken = token
        }
        val tools = McpBridge(this)
        val server = McpHttpServer(port, token) { body -> McpProtocol.handle(body, tools::invoke) }
        try {
            server.start()
            store.mcpStatus = "listening"
            bridge = tools
            http = server
        } catch (err: Exception) {
            store.mcpStatus = err.message ?: "bind failed"
            tools.close()
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ServiceStore(this).mcpEnabled || http == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        http?.stop()
        http = null
        bridge?.close()
        bridge = null
        val store = ServiceStore(this)
        if (store.mcpStatus == "listening") store.mcpStatus = ""
        super.onDestroy()
    }
}
