package com.ok2app.sms.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ok2app.sms.SmsGatewayApp
import com.ok2app.sms.service.SmsForegroundService
import kotlinx.coroutines.flow.first

class GatewayWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SmsGatewayApp
        val config = app.preferences.configFlow.first()

        if (!config.isConfigured) return Result.success()

        // Asegura que el service esté corriendo; START_STICKY lo reinicia solo
        // pero este watchdog actúa como red de seguridad adicional.
        SmsForegroundService.start(applicationContext)

        app.repository.sendHeartbeat()

        return Result.success()
    }
}
