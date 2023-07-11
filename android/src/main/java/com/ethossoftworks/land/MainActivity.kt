@file:Suppress("INLINE_FROM_HIGHER_PLATFORM") // https://youtrack.jetbrains.com/issue/KTIJ-20816/Bogus-error-Cannot-inline-bytecode-built-with-JVM-target-11-into-bytecode-that-is-being-built-with-JVM-target-1.8.

package com.ethossoftworks.land

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.ethossoftworks.land.common.service.file.FileHandlerContext
import com.ethossoftworks.land.common.service.file.IFileHandler
import com.ethossoftworks.land.common.ui.app.App
import org.koin.java.KoinJavaComponent.inject

class MainActivity : ComponentActivity() {
    private val fileHandler: IFileHandler by inject(IFileHandler::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        fileHandler.init(
            FileHandlerContext.Android(
                applicationContext = application,
                activity = this,
            )
        )

        setContent {
            App()
        }
    }
}