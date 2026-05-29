package com.ok2app.sms.ui.screens.dashboard

import android.app.Application
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

    private val _lastSyncTime = MutableStateFlow("—")
    private val _isSyncing = MutableStateFlow(false)
    private val _lastError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.configFlow,
        repository.stats,
        _lastSyncTime,
        _isSyncing,
        _lastError
    ) { config, stats, lastSync, isSyncing, lastError ->
        DashboardUiState(config, stats, lastSync, isSyncing, lastError)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    init {
        viewModelScope.launch {
            val config = repository.configFlow.first()
            if (config.isConfigured) SmsForegroundService.start(getApplication())
        }
    }

    fun togglePause() {
        viewModelScope.launch {
            val paused = uiState.value.config?.isPaused ?: false
            app.preferences.setPaused(!paused)
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            _lastError.value = null

            val result = repository.runPollCycle()

            _isSyncing.value = false
            _lastSyncTime.value = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            if (result is PollResult.Error) {
                _lastError.value = result.message
            }
        }
    }
}
