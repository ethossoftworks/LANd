package com.ethossoftworks.land.common.ui

import androidx.compose.ui.awt.ComposeWindow
import com.ethossoftworks.land.common.service.file.FileHandlerContext
import com.ethossoftworks.land.common.service.file.IFileHandler
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current

class DesktopAppViewInteractor(
    private val fileHandler: IFileHandler,
) : Interactor<Unit>(initialState = Unit) {

    fun onViewInitialized(window: ComposeWindow) {
        fileHandler.init(FileHandlerContext.Desktop(window))
    }

    fun onViewMounted(window: ComposeWindow) {
        if (Platform.current == Platform.MacOS) {
            window.rootPane.apply {
                putClientProperty("apple.awt.fullWindowContent", true)
                putClientProperty("apple.awt.windowTitleVisible", false)
                putClientProperty("apple.awt.transparentTitleBar", true)
            }
        }
    }
}