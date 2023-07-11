@file:Suppress("INLINE_FROM_HIGHER_PLATFORM") // https://youtrack.jetbrains.com/issue/KTIJ-20816/Bogus-error-Cannot-inline-bytecode-built-with-JVM-target-11-into-bytecode-that-is-being-built-with-JVM-target-1.8.

package com.ethossoftworks.land.common.ui.app

import androidx.compose.runtime.Composable
import com.ethossoftworks.land.common.coordinator.AppCoordinator
import com.ethossoftworks.land.common.ui.Route
import com.ethossoftworks.land.common.ui.common.theme.AppTheme
import com.ethossoftworks.land.common.ui.home.HomeScreen
import com.outsidesource.oskitcompose.lib.rememberInject
import com.outsidesource.oskitcompose.router.RouteSwitch

@Composable
fun App(coordinator: AppCoordinator = rememberInject()) {
    AppTheme {
        RouteSwitch(coordinator) {
            when (it) {
                is Route.Home -> HomeScreen()
            }
        }
    }
}
