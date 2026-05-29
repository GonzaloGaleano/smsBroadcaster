package com.ok2app.sms.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ok2app.sms.domain.model.GatewayConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gateway_prefs")

class GatewayPreferences(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_TOKEN = stringPreferencesKey("api_token")
        val GATEWAY_ID = stringPreferencesKey("gateway_id")
        val IS_PAUSED = booleanPreferencesKey("is_paused")
        val MAX_PER_MINUTE = intPreferencesKey("max_per_minute")
    }

    val configFlow: Flow<GatewayConfig> = context.dataStore.data.map { prefs ->
        GatewayConfig(
            baseUrl = prefs[Keys.BASE_URL] ?: "",
            apiToken = prefs[Keys.API_TOKEN] ?: "",
            gatewayId = prefs[Keys.GATEWAY_ID],
            isPaused = prefs[Keys.IS_PAUSED] ?: false,
            maxMessagesPerMinute = prefs[Keys.MAX_PER_MINUTE] ?: 5
        )
    }

    suspend fun saveBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.BASE_URL] = url }
    }

    suspend fun saveApiToken(token: String) {
        context.dataStore.edit { it[Keys.API_TOKEN] = token }
    }

    suspend fun saveGatewayId(id: String) {
        context.dataStore.edit { it[Keys.GATEWAY_ID] = id }
    }

    suspend fun setPaused(paused: Boolean) {
        context.dataStore.edit { it[Keys.IS_PAUSED] = paused }
    }

    suspend fun setMaxMessagesPerMinute(max: Int) {
        context.dataStore.edit { it[Keys.MAX_PER_MINUTE] = max }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
