package com.ok2app.sms

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.ok2app.sms.ui.navigation.NavGraph
import com.ok2app.sms.ui.navigation.Screen
import com.ok2app.sms.ui.theme.SMSOk2AppTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SMSOk2AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val app = application as SmsGatewayApp
                    val navController = rememberNavController()

                    val startDestination by produceState<String?>(initialValue = null) {
                        val config = app.preferences.configFlow.first()
                        value = if (config.isConfigured) Screen.Dashboard.route
                        else Screen.Setup.route
                    }

                    // Solicita SEND_SMS con un rationale claro al usuario
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { /* SmsSender verifica el permiso antes de cada envío */ }

                    LaunchedEffect(Unit) {
                        val permissions = buildList {
                            add(Manifest.permission.SEND_SMS)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        permissionLauncher.launch(permissions.toTypedArray())
                    }

                    if (startDestination == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        NavGraph(
                            navController = navController,
                            startDestination = startDestination!!
                        )
                    }
                }
            }
        }
    }
}
