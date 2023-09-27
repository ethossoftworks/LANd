package com.ethossoftworks.land

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.ethossoftworks.land.common.ui.app.App
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

        setContent {
            App()
        }
    }
}