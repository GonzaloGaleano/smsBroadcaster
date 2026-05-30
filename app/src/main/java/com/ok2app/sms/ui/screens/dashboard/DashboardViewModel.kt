package com.ok2app.sms.ui.screens.dashboard

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ok2app.sms.SmsGatewayApp
import com.ok2app.sms.data.repository.GatewayStats
import com.ok2app.sms.data.repository.PollResult
import com.ok2app.sms.domain.model.GatewayConfig
import com.ok2app.sms.service.SmsForegroundService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class DashboardUiState(
    val config: GatewayConfig? = null,
    val stats: GatewayStats = GatewayStats(),
    val lastSyncTime: String = "—",
    val isSyncing: Boolean = false,
    val lastError: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmsGatewayApp
    private val repository = app.repository

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        Log.d("DashboardViewModel", "init called")
        viewModelScope.launch {
            // Observar configuración
            launch {
                repository.configFlow.collect { config ->
                    Log.d("DashboardViewModel", "Config update: $config")
                    _uiState.update { it.copy(config = config) }
                    if (config.isConfigured && !config.isPaused) {
                        SmsForegroundService.start(getApplication())
                    }
                }
            }
            // Observar estadísticas
            launch {
                repository.stats.collect { stats ->
                    Log.d("DashboardViewModel", "Stats update: $stats")
                    _uiState.update { it.copy(stats = stats) }
                }
            }
        }
    }

    fun togglePause() {
        viewModelScope.launch {
            val paused = _uiState.value.config?.isPaused ?: false
            app.preferences.setPaused(!paused)
        }
    }

    fun syncNow() {
        Log.d("DashboardViewModel", "syncNow() CLICKED")
        if (_uiState.value.isSyncing) return

        viewModelScope.launch {
            Log.d("DashboardViewModel", "syncNow() STARTING COROUTINE")
            _uiState.update { it.copy(isSyncing = true, lastError = null) }

            try {
                // TEST DE CONEXION RAPIDO
                val test = repository.testApiConnection()
                Log.d("DashboardViewModel", "Connection Test: $test")
                
                val result = repository.runPollCycle()
                Log.d("DashboardViewModel", "syncNow() RESULT: $result")

                _uiState.update { state ->
                    state.copy(
                        lastSyncTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                        lastError = if (result is PollResult.Error) result.message 
                                   else if (test.isFailure) "Error de conexión: ${test.exceptionOrNull()?.message}"
                                   else null
                    )
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "syncNow() EXCEPTION", e)
                _uiState.update { it.copy(lastError = e.message) }
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }
}
