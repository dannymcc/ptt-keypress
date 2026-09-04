package io.dmcc.pttkeypress.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.dmcc.pttkeypress.MainActivity
import io.dmcc.pttkeypress.PttKeypressApp
import io.dmcc.pttkeypress.R

class PttForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("PTT Keypress")
            .setContentText("Waiting for PTT → VoxDMR")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        (application as PttKeypressApp).bleManager.armAll()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        (application as PttKeypressApp).bleManager.armAll()
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "PTT connection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps sleeping PTT buttons armed and bridges them to VoxDMR."
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "ptt_keypress_connection"
        private const val NOTIFICATION_ID = 621

        fun start(context: Context) {
            val intent = Intent(context, PttForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
