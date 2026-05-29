package com.ok2app.sms.ui.navigation

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
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToHistory = { navController.navigate(Screen.History.route) }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
