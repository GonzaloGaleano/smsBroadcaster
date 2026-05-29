package com.ok2app.sms

import android.app.Application
import com.ok2app.sms.data.local.db.SmsDatabase
import com.ok2app.sms.data.local.prefs.GatewayPreferences
import com.ok2app.sms.data.repository.GatewayRepository

class SmsGatewayApp : Application() {
    val preferences by lazy { GatewayPreferences(this) }
    val database by lazy { SmsDatabase.create(this) }
    val repository by lazy { GatewayRepository(this, preferences, database) }
}
