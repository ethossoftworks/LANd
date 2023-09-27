package com.ethossoftworks.land.common.ui

import androidx.compose.ui.awt.ComposeWindow
import com.outsidesource.oskitkmp.file.IKMPFileHandler
import com.outsidesource.oskitkmp.file.KMPFileHandlerContext
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current

class DesktopAppViewInteractor(
    private val fileHandler: IKMPFileHandler,
) : Interactor<Unit>(initialState = Unit) {

    fun onViewInitialized(window: ComposeWindow) {
        fileHandler.init(KMPFileHandlerContext(window))
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