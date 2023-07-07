import androidx.compose.ui.window.application
import com.ethossoftworks.land.common.DIPlatformContext
import com.ethossoftworks.land.common.initDI
import com.ethossoftworks.land.common.ui.DesktopApp

fun main() = application {
    initDI(DIPlatformContext.Desktop)
    DesktopApp()
}
