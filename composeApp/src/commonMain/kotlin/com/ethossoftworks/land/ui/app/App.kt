package com.ethossoftworks.land.ui.app

import androidx.compose.runtime.Composable
import com.ethossoftworks.land.coordinator.AppCoordinator
import com.ethossoftworks.land.ui.Route
import com.ethossoftworks.land.ui.common.theme.AppTheme
import com.ethossoftworks.land.ui.home.HomeScreen
import com.ethossoftworks.land.ui.openSourceLicenses.OpenSourceLicensesScreen
import com.outsidesource.oskitcompose.lib.rememberInject
import com.outsidesource.oskitcompose.router.RouteSwitch

@Composable
fun App(coordinator: AppCoordinator = rememberInject()) {
    AppTheme {
        RouteSwitch(coordinator) {
            when (it) {
                is Route.Home -> HomeScreen()
                is Route.OpenSourceLicenses -> OpenSourceLicensesScreen()
            }
        }
    }
}
