package com.ok2app.sms.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ok2app.sms.ui.screens.dashboard.DashboardScreen
import com.ok2app.sms.ui.screens.history.HistoryScreen
import com.ok2app.sms.ui.screens.setup.SetupScreen

sealed class Screen(val route: String) {
    object Setup : Screen("setup")
    object Dashboard : Screen("dashboard")
    object History : Screen("history")
}

@Composable
fun NavGraph(navController: NavHostController, startDestination: String) {
    Log.d("NavGraph", "Rendering NavGraph with startDestination: $startDestination")
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Setup.route) {
            Log.d("NavGraph", "Navigating to SetupScreen")
            SetupScreen(
                onSetupComplete = {
                    Log.d("NavGraph", "Setup complete, navigating to Dashboard")
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Dashboard.route) {
            Log.d("NavGraph", "Navigating to DashboardScreen")
            DashboardScreen(
                onNavigateToHistory = { 
                    Log.d("NavGraph", "Navigating to History")
                    navController.navigate(Screen.History.route) 
                }
            )
        }
        composable(Screen.History.route) {
            Log.d("NavGraph", "Navigating to HistoryScreen")
            HistoryScreen(
                onNavigateBack = { 
                    Log.d("NavGraph", "Navigating back from History")
                    navController.popBackStack() 
                }
            )
        }
    }
}
