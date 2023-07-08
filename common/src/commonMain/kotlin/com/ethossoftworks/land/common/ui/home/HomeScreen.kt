@file:Suppress("INLINE_FROM_HIGHER_PLATFORM") // https://youtrack.jetbrains.com/issue/KTIJ-20816/Bogus-error-Cannot-inline-bytecode-built-with-JVM-target-11-into-bytecode-that-is-being-built-with-JVM-target-1.8.

package com.ethossoftworks.land.common.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute

@Composable
fun HomeScreen(
    interactor: HomeScreenViewInteractor = rememberInjectForRoute()
) {
    val state by interactor.collectAsState()

    LaunchedEffect(Unit) {
        interactor.viewMounted()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (device in state.discoveredDevices.values) {
            Text(device.name)
        }
    }
}