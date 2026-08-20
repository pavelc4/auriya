package dev.auriya.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.auriya.app.MainActivity

class BenchmarkRecordingService : Service() {
    companion object {
        const val CHANNEL_ID = "auriya_benchmark_channel"
        const val NOTIFICATION_ID = 1002

        fun start(
            context: Context,
            gameTitle: String,
        ) {
            runCatching {
                val intent =
                    Intent(context, BenchmarkRecordingService::class.java).apply {
                        putExtra("game_title", gameTitle)
                    }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, BenchmarkRecordingService::class.java))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val gameTitle = intent?.getStringExtra("game_title") ?: "Game"
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setContentTitle("Auriya Benchmark Active")
                .setContentText("Tracking frame telemetry for $gameTitle")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Auriya Benchmark Service",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Keeps benchmark telemetry recorder active during gameplay"
                }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
