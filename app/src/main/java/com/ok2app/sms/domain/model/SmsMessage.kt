package com.ok2app.sms.domain.model

data class SmsMessage(
    val id: Int,
    val recipient: String,
    val content: String,
    val priority: Int = 0
)
