import androidx.compose.ui.window.application
import com.ethossoftworks.land.DIPlatformContext
import com.ethossoftworks.land.initDI
import com.ethossoftworks.land.ui.DesktopApp

fun main() = application {
    initDI(DIPlatformContext())
    DesktopApp()
}
