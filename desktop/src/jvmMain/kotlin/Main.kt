import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ethossoftworks.land.common.initKoin
import com.ethossoftworks.land.common.ui.App

fun main() = application {
    initKoin()

    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
