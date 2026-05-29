package com.ok2app.sms.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {

    @Query("SELECT * FROM sms_log ORDER BY processedAt DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<SmsLogEntity>>

    @Query("SELECT COUNT(*) FROM sms_log WHERE statusCode = :status")
    fun getCountByStatus(status: String): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM sms_log " +
        "WHERE statusCode IN ('sending', 'sent') AND processedAt > :since"
    )
    suspend fun getRecentSentCount(since: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(log: SmsLogEntity)

    @Query("DELETE FROM sms_log WHERE processedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
