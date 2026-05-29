package com.ok2app.sms.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_model") val deviceModel: String,
    @SerializedName("app_version") val appVersion: String
)

data class HeartbeatRequest(
    @SerializedName("gateway_id") val gatewayId: String,
    val status: String
)

data class StatusUpdateRequest(
    val status: String,
    @SerializedName("error_message") val errorMessage: String? = null
)
