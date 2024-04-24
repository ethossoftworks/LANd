package com.ethossoftworks.land.lib

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

actual object SystemScreenOpener {
    actual fun openSettings() {
        UIApplication.sharedApplication.openURL(NSURL(string = UIApplicationOpenSettingsURLString))
    }
}