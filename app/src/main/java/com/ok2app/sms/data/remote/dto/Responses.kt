package com.ok2app.sms.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RegisterResponse(
    @SerializedName("gateway_id") val gatewayId: String
)

data class PendingMessageDto(
    val id: Int,
    val recipient: String,
    val content: String,
    val priority: Int = 0
)

data class PendingMessagesResponse(
    val data: List<PendingMessageDto>
)
