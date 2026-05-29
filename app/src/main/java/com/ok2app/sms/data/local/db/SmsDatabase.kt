package com.ok2app.sms.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SmsLogEntity::class], version = 1, exportSchema = true)
abstract class SmsDatabase : RoomDatabase() {

    abstract fun smsLogDao(): SmsLogDao

    companion object {
        fun create(context: Context): SmsDatabase =
            Room.databaseBuilder(context, SmsDatabase::class.java, "sms_gateway.db").build()
    }
}
