package com.ok2app.sms.ui.screens.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ok2app.sms.SmsGatewayApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SetupUiState(
    val baseUrl: String = "",
    val apiToken: String = "",
    val maxPerMinute: Int = 5,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmsGatewayApp
    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun onBaseUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(baseUrl = url, errorMessage = null)
    }

    fun onApiTokenChange(token: String) {
        _uiState.value = _uiState.value.copy(apiToken = token, errorMessage = null)
    }

    fun onMaxPerMinuteChange(max: Int) {
        _uiState.value = _uiState.value.copy(maxPerMinute = max)
    }

    fun connect() {
        val state = _uiState.value
        if (!validate(state)) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)

            app.preferences.setMaxMessagesPerMinute(state.maxPerMinute)

            val result = app.repository.register(state.baseUrl.trim(), state.apiToken.trim())

            _uiState.value = if (result.isSuccess) {
                _uiState.value.copy(isLoading = false, isSuccess = true)
            } else {
                _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Error de conexión"
                )
            }
        }
    }

    private fun validate(state: SetupUiState): Boolean {
        val url = state.baseUrl.trim()
        val token = state.apiToken.trim()

        val error = when {
            url.isBlank() -> "La URL base es requerida"
            !url.startsWith("http://") && !url.startsWith("https://") ->
                "La URL debe comenzar con http:// o https://"
            token.isBlank() -> "El API token es requerido"
            else -> null
        }
        if (error != null) {
            _uiState.value = state.copy(errorMessage = error)
            return false
        }
        return true
    }
}
