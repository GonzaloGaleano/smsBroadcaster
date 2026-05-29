package com.ok2app.sms.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ok2app.sms.MainActivity
import com.ok2app.sms.SmsGatewayApp
import com.ok2app.sms.data.repository.GatewayStats
import com.ok2app.sms.worker.GatewayWatchdogWorker
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class SmsForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isPaused = false
    private var lastStats = GatewayStats()

    companion object {
        private const val CHANNEL_ID = "sms_gateway_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "com.ok2app.sms.PAUSE"
        const val ACTION_RESUME = "com.ok2app.sms.RESUME"
        private const val POLLING_INTERVAL_MS = 60_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SmsForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SmsForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Iniciando…", 0, 0, 0, false))
        scheduleWatchdog()
        startPollingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> setGatewayPaused(true)
            ACTION_RESUME -> setGatewayPaused(false)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun setGatewayPaused(paused: Boolean) {
        serviceScope.launch {
            (application as SmsGatewayApp).preferences.setPaused(paused)
        }
    }

    private fun startPollingLoop() {
        val repository = (application as SmsGatewayApp).repository

        serviceScope.launch {
            launch {
                repository.configFlow.collect { config ->
                    isPaused = config.isPaused
                    refreshNotification()
                }
            }

            launch {
                repository.stats.collect { stats ->
                    lastStats = stats
                    refreshNotification()
                }
            }

            while (isActive) {
                if (!isPaused) {
                    repository.runPollCycle()
                }
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    private fun scheduleWatchdog() {
        val request = PeriodicWorkRequestBuilder<GatewayWatchdogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "gateway_watchdog",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun refreshNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                status = if (isPaused) "En pausa" else "Activo",
                pending = lastStats.pendingCount,
                sent = lastStats.sentCount,
                failed = lastStats.failedCount,
                paused = isPaused
            )
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SMS Gateway",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Estado del gateway de mensajes SMS" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(
        status: String,
        pending: Int,
        sent: Int,
        failed: Int,
        paused: Boolean
    ): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val togglePendingIntent = PendingIntent.getService(
            this, 0,
            Intent(this, SmsForegroundService::class.java).apply {
                action = if (paused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val toggleAction = NotificationCompat.Action(
            android.R.drawable.ic_media_pause,
            if (paused) "Reanudar" else "Pausar",
            togglePendingIntent
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("SMS Gateway — $status")
            .setContentText("Pendientes: $pending · Enviados: $sent · Fallas: $failed")
            .setContentIntent(openApp)
            .addAction(toggleAction)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
