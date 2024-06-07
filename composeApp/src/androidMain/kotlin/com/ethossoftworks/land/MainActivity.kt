package com.ethossoftworks.land

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.ethossoftworks.land.ui.app.App
import com.outsidesource.oskitcompose.systemui.KMPAppLifecycleObserver
import com.outsidesource.oskitcompose.systemui.KMPAppLifecycleObserverContext
import com.outsidesource.oskitkmp.file.IKMPFileHandler
import com.outsidesource.oskitkmp.file.KMPFileHandlerContext
import org.koin.java.KoinJavaComponent.inject

class MainActivity : ComponentActivity() {
    private val fileHandler: IKMPFileHandler by inject(IKMPFileHandler::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        fileHandler.init(
            KMPFileHandlerContext(
                applicationContext = application,
                activity = this,
            )
        )

        KMPAppLifecycleObserver.init(KMPAppLifecycleObserverContext())

        setContent {
            App()
        }
    }
}