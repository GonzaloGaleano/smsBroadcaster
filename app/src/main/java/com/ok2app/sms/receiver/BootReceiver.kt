package com.ok2app.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ok2app.sms.SmsGatewayApp
import com.ok2app.sms.service.SmsForegroundService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as SmsGatewayApp
        // runBlocking es aceptable aquí porque BroadcastReceiver.onReceive() no puede ser suspend.
        // La lectura de DataStore es rápida (disco local, valor ya cacheado).
        val config = runBlocking { app.preferences.configFlow.first() }

        if (config.isConfigured) {
            SmsForegroundService.start(context)
        }
    }
}
