package com.ok2app.sms.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ok2app.sms.SmsGatewayApp
import com.ok2app.sms.data.local.db.SmsLogEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    val logs: StateFlow<List<SmsLogEntity>> =
        (application as SmsGatewayApp).repository.recentLogs
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )
}
