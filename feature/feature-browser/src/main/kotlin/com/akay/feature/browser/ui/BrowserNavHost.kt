package com.akay.feature.browser.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun BrowserNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "browser"
    ) {
        composable("browser") {
            BrowserScreen()
        }
    }
}
