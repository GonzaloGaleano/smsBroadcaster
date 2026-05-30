package com.ok2app.sms.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.ok2app.sms.BuildConfig
import com.ok2app.sms.data.local.db.SmsDatabase
import com.ok2app.sms.data.local.db.SmsLogEntity
import com.ok2app.sms.data.local.prefs.GatewayPreferences
import com.ok2app.sms.data.remote.RetrofitClient
import com.ok2app.sms.data.remote.dto.HeartbeatRequest
import com.ok2app.sms.data.remote.dto.RegisterRequest
import com.ok2app.sms.data.remote.dto.StatusUpdateRequest
import com.ok2app.sms.domain.model.GatewayConfig
import com.ok2app.sms.domain.model.SmsMessage
import com.ok2app.sms.service.SmsSender
import com.ok2app.sms.worker.RateLimiter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class GatewayStats(
    val pendingCount: Int = 0,
    val sentCount: Int = 0,
    val failedCount: Int = 0
)

sealed class PollResult {
    data class Success(val processed: Int) : PollResult()
    data class Skipped(val reason: String) : PollResult()
    data class Error(val message: String) : PollResult()
}

class GatewayRepository(
    private val context: Context,
    private val preferences: GatewayPreferences,
    private val database: SmsDatabase
) {
    private val dao = database.smsLogDao()
    private val smsSender = SmsSender(context)
    private val rateLimiter = RateLimiter(dao)

    val configFlow: Flow<GatewayConfig> = preferences.configFlow

    val recentLogs: Flow<List<SmsLogEntity>> = dao.getRecentLogs()

    val stats: Flow<GatewayStats> = combine(
        dao.getCountByStatus("pending"),
        dao.getCountByStatus("sent"),
        dao.getCountByStatus("failed")
    ) { pending, sent, failed ->
        GatewayStats(pending, sent, failed)
    }

    suspend fun register(baseUrl: String, apiToken: String): Result<Unit> {
        return try {
            val api = RetrofitClient.create(baseUrl, apiToken)
            val response = api.register(
                RegisterRequest(
                    deviceName = Build.MODEL,
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    appVersion = BuildConfig.VERSION_NAME
                )
            )
            if (response.isSuccessful && response.body() != null) {
                preferences.saveBaseUrl(baseUrl)
                preferences.saveApiToken(apiToken)
                preferences.saveGatewayId(response.body()!!.gatewayId)
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Sin respuesta"
                Result.failure(Exception("Error ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendHeartbeat(): Result<Unit> {
        return try {
            val config = preferences.configFlow.first()
            if (!config.isConfigured) return Result.failure(Exception("Gateway no configurado"))
            val api = RetrofitClient.create(config.baseUrl, config.apiToken)
            val response = api.heartbeat(HeartbeatRequest(config.gatewayId!!, "active"))
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Heartbeat fallido: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testApiConnection(): Result<String> {
        val config = preferences.configFlow.first()
        if (config.baseUrl.isBlank()) return Result.failure(Exception("URL vacía"))
        return try {
            val api = RetrofitClient.create(config.baseUrl, config.apiToken)
            val response = api.getPendingMessages()
            if (response.isSuccessful) Result.success("Conexión OK: ${response.code()}")
            else Result.failure(Exception("Error API: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun runPollCycle(): PollResult {
        Log.d("GatewayRepository", "runPollCycle() started")
        val config = preferences.configFlow.first()
        Log.d("GatewayRepository", "Config: configured=${config.isConfigured}, paused=${config.isPaused}")

        if (!config.isConfigured) return PollResult.Skipped("No configurado")
        if (config.isPaused) return PollResult.Skipped("En pausa")

        return try {
            Log.d("GatewayRepository", "Creating API client with baseUrl: ${config.baseUrl}")
            val api = RetrofitClient.create(config.baseUrl, config.apiToken)
            Log.d("GatewayRepository", "Fetching pending messages...")
            val pendingResponse = api.getPendingMessages()
            Log.d("GatewayRepository", "Fetch response: code=${pendingResponse.code()}, success=${pendingResponse.isSuccessful}")

            if (!pendingResponse.isSuccessful) {
                return PollResult.Error("Fetch fallido: ${pendingResponse.code()}")
            }

            val messages = pendingResponse.body()?.data?.map {
                SmsMessage(it.id, it.recipient, it.content, it.priority)
            } ?: emptyList()
            Log.d("GatewayRepository", "Found ${messages.size} pending messages")

            var processed = 0

            for (message in messages) {
                if (!rateLimiter.canSend(config.maxMessagesPerMinute)) {
                    Log.w("GatewayRepository", "Rate limit reached, stopping for now")
                    break
                }

                Log.d("GatewayRepository", "Claiming message ${message.id}")
                val claimResponse = api.claimMessage(message.id)
                if (!claimResponse.isSuccessful) {
                    Log.e("GatewayRepository", "Failed to claim message ${message.id}: ${claimResponse.code()}")
                    continue
                }

                dao.insertOrUpdate(
                    SmsLogEntity(
                        remoteId = message.id,
                        recipient = message.recipient,
                        statusCode = "sending",
                        errorMessage = null,
                        processedAt = System.currentTimeMillis()
                    )
                )

                Log.d("GatewayRepository", "Sending SMS to ${message.recipient}")
                val smsResult = smsSender.send(message.recipient, message.content)
                Log.d("GatewayRepository", "SMS send result: $smsResult")

                val finalStatus: String
                val errorMsg: String?
                when (smsResult) {
                    is SmsSender.SmsResult.Success -> { finalStatus = "sent"; errorMsg = null }
                    is SmsSender.SmsResult.Failure -> { finalStatus = "failed"; errorMsg = smsResult.reason }
                }

                Log.d("GatewayRepository", "Updating remote status for ${message.id} to $finalStatus")
                api.updateStatus(message.id, StatusUpdateRequest(finalStatus, errorMsg))

                dao.insertOrUpdate(
                    SmsLogEntity(
                        remoteId = message.id,
                        recipient = message.recipient,
                        statusCode = finalStatus,
                        errorMessage = errorMsg,
                        processedAt = System.currentTimeMillis()
                    )
                )

                processed++
            }

            Log.d("GatewayRepository", "Poll cycle completed. Processed $processed messages")
            PollResult.Success(processed)
        } catch (e: Exception) {
            Log.e("GatewayRepository", "Error in runPollCycle", e)
            PollResult.Error(e.message ?: "Error desconocido")
        }
    }
}
