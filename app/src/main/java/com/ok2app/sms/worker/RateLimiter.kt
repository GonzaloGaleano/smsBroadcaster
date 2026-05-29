package com.ok2app.sms.worker

import com.ok2app.sms.data.local.db.SmsLogDao

class RateLimiter(private val dao: SmsLogDao) {

    suspend fun canSend(maxPerMinute: Int): Boolean {
        val oneMinuteAgo = System.currentTimeMillis() - 60_000L
        val recentCount = dao.getRecentSentCount(since = oneMinuteAgo)
        return recentCount < maxPerMinute
    }
}
