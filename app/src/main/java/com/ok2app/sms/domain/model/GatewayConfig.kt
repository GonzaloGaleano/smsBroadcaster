package com.ok2app.sms.domain.model

data class GatewayConfig(
    val baseUrl: String,
    val apiToken: String,
    val gatewayId: String?,
    val isPaused: Boolean,
    val maxMessagesPerMinute: Int
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiToken.isNotBlank() && gatewayId != null

    val isReadyToSend: Boolean
        get() = isConfigured && !isPaused
}
