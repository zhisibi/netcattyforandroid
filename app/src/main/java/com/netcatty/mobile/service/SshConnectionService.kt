package com.netcatty.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.netcatty.mobile.MainActivity
import com.netcatty.mobile.R

/**
 * SSH 连接前台服务。
 * 保持 SSH 连接在后台不被系统杀死。
 *
 * 集成方式：
 * 1. TerminalViewModel 连接成功后调用 startService()
 * 2. SftpViewModel 连接成功后调用 startService()
 * 3. 断开最后一个连接时调用 stopService()
 */
class SshConnectionService : Service() {

    private val binder = LocalBinder()
    private var activeConnectionCount = 0
    private var currentHostLabel: String? = null

    inner class LocalBinder : Binder() {
        fun getService(): SshConnectionService = this@SshConnectionService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val label = intent.getStringExtra(EXTRA_HOST_LABEL) ?: "Server"
                activeConnectionCount++
                currentHostLabel = label
                updateNotification()
            }
            ACTION_DISCONNECT -> {
                activeConnectionCount = (activeConnectionCount - 1).coerceAtLeast(0)
                if (activeConnectionCount <= 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateNotification()
                }
            }
            ACTION_UPDATE -> {
                currentHostLabel = intent.getStringExtra(EXTRA_HOST_LABEL) ?: currentHostLabel
                updateNotification()
            }
        }
        return START_STICKY
    }

    private fun updateNotification() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (activeConnectionCount <= 1) {
            "SSH: $currentHostLabel"
        } else {
            "SSH: $activeConnectionCount connections"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Connection active")
            .setSmallIcon(R.drawable.ic_terminal_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSH Connections",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps SSH connections alive in background"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "ssh_connection"
        const val NOTIFICATION_ID = 1

        const val ACTION_CONNECT = "com.netcatty.mobile.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.netcatty.mobile.ACTION_DISCONNECT"
        const val ACTION_UPDATE = "com.netcatty.mobile.ACTION_UPDATE"
        const val EXTRA_HOST_LABEL = "host_label"
    }
}
