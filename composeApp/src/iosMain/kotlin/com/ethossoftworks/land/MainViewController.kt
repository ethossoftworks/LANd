package com.ethossoftworks.land

import com.ethossoftworks.land.ui.app.App
import com.outsidesource.oskitcompose.lib.koinInjector
import com.outsidesource.oskitcompose.systemui.KMPAppLifecycleObserver
import com.outsidesource.oskitcompose.systemui.KMPAppLifecycleObserverContext
import com.outsidesource.oskitcompose.uikit.OSComposeUIViewController
import com.outsidesource.oskitkmp.file.IKMPFileHandler
import com.outsidesource.oskitkmp.file.KMPFileHandlerContext
import org.koin.core.component.inject
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val fileHandler by koinInjector.inject<IKMPFileHandler>()
    KMPAppLifecycleObserver.init(KMPAppLifecycleObserverContext())

    return OSComposeUIViewController {
        App()
    }.apply {
        fileHandler.init(KMPFileHandlerContext(this))
    }
}
