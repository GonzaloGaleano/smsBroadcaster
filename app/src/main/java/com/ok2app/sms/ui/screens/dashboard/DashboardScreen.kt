package com.ok2app.sms.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToHistory: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Gateway") },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "Historial")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionCard(
                isConfigured = state.config?.isConfigured == true,
                isPaused = state.config?.isPaused == true,
                lastSyncTime = state.lastSyncTime
            )

            StatsCard(
                pending = state.stats.pendingCount,
                sent = state.stats.sentCount,
                failed = state.stats.failedCount
            )

            ControlsCard(
                isPaused = state.config?.isPaused == true,
                isSyncing = state.isSyncing,
                onTogglePause = viewModel::togglePause,
                onSyncNow = viewModel::syncNow
            )

            if (state.lastError != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = state.lastError!!,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(isConfigured: Boolean, isPaused: Boolean, lastSyncTime: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dotColor = when {
                    !isConfigured -> Color.Gray
                    isPaused -> Color(0xFFFF9800)
                    else -> Color(0xFF4CAF50)
                }
                Surface(
                    modifier = Modifier.size(12.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = dotColor
                ) {}
                Text(
                    text = when {
                        !isConfigured -> "Sin configurar"
                        isPaused -> "En pausa"
                        else -> "Conectado"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Última sync", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(lastSyncTime, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatsCard(pending: Int, sent: Int, failed: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("Pendientes", pending, MaterialTheme.colorScheme.primary)
            StatItem("Enviados", sent, Color(0xFF4CAF50))
            StatItem("Fallas", failed, MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.headlineMedium,
            color = color
        )
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ControlsCard(
    isPaused: Boolean,
    isSyncing: Boolean,
    onTogglePause: () -> Unit,
    onSyncNow: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPaused) "Gateway en pausa" else "Gateway activo",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(checked = !isPaused, onCheckedChange = { onTogglePause() })
            }

            Button(
                onClick = onSyncNow,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSyncing // Removido !isPaused para permitir testeo
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isSyncing) "Sincronizando…" else "Sincronizar ahora")
            }
        }
    }
}
