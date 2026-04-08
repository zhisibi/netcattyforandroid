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
import androidx.core.app.NotificationManagerCompat
import com.netcatty.mobile.MainActivity
import com.netcatty.mobile.R

/**
 * SFTP 文件传输前台服务。
 * 支持后台传输、进度通知、取消操作。
 */
class SftpTransferService : Service() {

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): SftpTransferService = this@SftpTransferService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createInitialNotification())
        return START_NOT_STICKY
    }

    fun updateProgress(fileName: String, progress: Int, speed: String?) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Transferring: $fileName")
            .setProgress(100, progress, false)
            .setSmallIcon(R.drawable.ic_terminal_notification)
            .setOngoing(true)
            .apply {
                if (speed != null) setContentText(speed)
            }
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun createInitialNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Netcatty")
            .setContentText("Preparing transfer…")
            .setSmallIcon(R.drawable.ic_terminal_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SFTP Transfers",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows SFTP file transfer progress"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "sftp_transfer"
        const val NOTIFICATION_ID = 2
    }
}
