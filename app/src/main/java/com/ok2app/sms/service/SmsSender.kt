package com.ok2app.sms.service

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

class SmsSender(private val context: Context) {

    sealed class SmsResult {
        object Success : SmsResult()
        data class Failure(val reason: String) : SmsResult()
    }

    suspend fun send(recipient: String, content: String): SmsResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return SmsResult.Failure("Permiso SEND_SMS no otorgado")
        }

        val resultChannel = Channel<Int>(Channel.CONFLATED)
        val action = "SMS_SENT_${System.nanoTime()}"

        val sentIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(action),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                resultChannel.trySend(resultCode)
            }
        }

        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(content)
            if (parts.size == 1) {
                smsManager.sendTextMessage(recipient, null, content, sentIntent, null)
            } else {
                // Para mensajes largos sólo monitoreamos el intent de la primera parte
                val sentIntents = ArrayList<PendingIntent>(parts.size).apply {
                    add(sentIntent)
                    repeat(parts.size - 1) { add(PendingIntent.getBroadcast(
                        context, 0,
                        Intent(action),
                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                    )) }
                }
                smsManager.sendMultipartTextMessage(recipient, null, parts, sentIntents, null)
            }

            val resultCode = withTimeoutOrNull(30_000L) { resultChannel.receive() }
                ?: return SmsResult.Failure("Timeout esperando confirmación de envío")

            if (resultCode == Activity.RESULT_OK) SmsResult.Success
            else SmsResult.Failure("SMS rechazado por el modem (código $resultCode)")
        } catch (e: Exception) {
            SmsResult.Failure(e.message ?: "Error de SMS desconocido")
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
