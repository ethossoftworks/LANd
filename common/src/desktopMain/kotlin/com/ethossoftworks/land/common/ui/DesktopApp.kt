package com.ethossoftworks.land.common.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import com.ethossoftworks.land.common.service.file.FileHandlerContext
import com.ethossoftworks.land.common.service.file.IFileHandler
import com.outsidesource.oskitcompose.lib.rememberInject
import com.outsidesource.oskitcompose.window.SizedWindow
import com.outsidesource.oskitcompose.window.rememberPersistedWindowState
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current
import java.awt.Dimension

@Composable
fun ApplicationScope.DesktopApp() {
    val windowState = rememberPersistedWindowState(
        node = "com.ethossoftworks.LANd",
        initialSize = Dimension(800, 600),
    )

    SizedWindow(
        onCloseRequest = ::exitApplication,
        minWindowSize = Dimension(800, 600),
        state = windowState,
        title = "LANd",
    ) {
        val fileHandler = rememberInject<IFileHandler>()

        DisposableEffect(Unit) {
            fileHandler.init(FileHandlerContext.Desktop(window))
            onDispose {  }
        }

        if (Platform.current == Platform.MacOS) {
            LaunchedEffect(Unit) {
                window.rootPane.apply {
                    putClientProperty("apple.awt.fullWindowContent", true)
                    putClientProperty("apple.awt.windowTitleVisible", false)
                    putClientProperty("apple.awt.transparentTitleBar", true)
                }
            }

            WindowDraggableArea(modifier = Modifier.fillMaxWidth().height(32.dp))
        }

        App()
    }
}