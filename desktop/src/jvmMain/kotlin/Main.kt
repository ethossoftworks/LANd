import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ethossoftworks.land.common.DIPlatformContext
import com.ethossoftworks.land.common.initDI
import com.ethossoftworks.land.common.ui.App

fun main() = application {
    initDI(DIPlatformContext.Desktop)

    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
