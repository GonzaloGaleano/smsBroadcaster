package com.ok2app.sms.data.remote.api

import com.ok2app.sms.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface GatewayApiService {

    @POST("api/sms-gateway/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("api/sms-gateway/heartbeat")
    suspend fun heartbeat(@Body request: HeartbeatRequest): Response<Unit>

    @GET("api/sms-gateway/messages/pending")
    suspend fun getPendingMessages(): Response<PendingMessagesResponse>

    @POST("api/sms-gateway/messages/{id}/claim")
    suspend fun claimMessage(@Path("id") id: Int): Response<PendingMessageDto>

    @POST("api/sms-gateway/messages/{id}/status")
    suspend fun updateStatus(
        @Path("id") id: Int,
        @Body request: StatusUpdateRequest
    ): Response<Unit>
}
