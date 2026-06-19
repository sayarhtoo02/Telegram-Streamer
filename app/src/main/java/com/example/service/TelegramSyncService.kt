package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.tdlib.TdLibClient

class TelegramSyncService : Service() {

    companion object {
        private const val TAG = "TelegramSyncService"
        private const val NOTIFICATION_CHANNEL_ID = "tg_streamer_sync_channel"
        private const val NOTIFICATION_ID = 4529
        
        fun startSyncService(context: Context) {
            val intent = Intent(context, TelegramSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopSyncService(context: Context) {
            val intent = Intent(context, TelegramSyncService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var tdLibClient: TdLibClient

    override fun onCreate() {
        super.onCreate();
        Log.i(TAG, "Creating Telegram Foreground Sync Service")
        tdLibClient = TdLibClient.getInstance(this)
        
        createNotificationChannel()
        startForegroundServiceCompact()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Sync service started")
        return START_STICKY
    }

    private fun startForegroundServiceCompact() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TG Streamer is Active")
            .setContentText("Connected directly to Telegram MTProto session.")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "TG Streamer Sync Status"
            val descriptionText = "Shows active connection state of direct streaming MTProto connection."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Destroying Telegram Foreground Sync Service")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
