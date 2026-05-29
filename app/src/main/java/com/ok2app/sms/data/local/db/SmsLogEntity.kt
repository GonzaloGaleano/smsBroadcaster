package com.ok2app.sms.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_log")
data class SmsLogEntity(
    @PrimaryKey val remoteId: Int,
    val recipient: String,
    val statusCode: String,
    val errorMessage: String?,
    val processedAt: Long,
    val retryCount: Int = 0
)
