@file:OptIn(ExperimentalResourceApi::class)

package com.ethossoftworks.land.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import com.ethossoftworks.land.ui.app.App
import com.outsidesource.oskitcompose.lib.rememberInject
import com.outsidesource.oskitcompose.systemui.KMPAppLifecycleObserver
import com.outsidesource.oskitcompose.systemui.KMPAppLifecycleObserverContext
import com.outsidesource.oskitcompose.systemui.KMPWindowInsetsHolder
import com.outsidesource.oskitcompose.systemui.LocalKMPWindowInsets
import com.outsidesource.oskitcompose.window.SizedWindow
import com.outsidesource.oskitcompose.window.rememberPersistedWindowState
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current
import land.composeapp.generated.resources.Res
import land.composeapp.generated.resources.window_icon
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension

@Composable
fun ApplicationScope.DesktopApp(
    interactor: DesktopAppViewInteractor = rememberInject(),
) {
    val windowState = rememberPersistedWindowState(
        node = "com.ethossoftworks.LANd.window",
        initialSize = Dimension(800, 600),
    )

    SizedWindow(
        onCloseRequest = ::exitApplication,
        minWindowSize = Dimension(800, 600),
        icon = painterResource(Res.drawable.window_icon),
        state = windowState,
        title = "LANd",
    ) {

        DisposableEffect(Unit) {
            interactor.onViewInitialized(window)
            onDispose {  }
        }

        LaunchedEffect(Unit) {
            interactor.onViewMounted(window)
            KMPAppLifecycleObserver.init(KMPAppLifecycleObserverContext(window))
        }

        if (Platform.current == Platform.MacOS) {
            WindowDraggableArea(modifier = Modifier.fillMaxWidth().height(24.dp))
        }

        CompositionLocalProvider(
            LocalKMPWindowInsets provides remember {
                if (Platform.current == Platform.MacOS) KMPWindowInsetsHolder(top = 24.dp) else null
            }
        ) {
            App()
        }
    }
}