package com.ethossoftworks.land.common.resources

import com.outsidesource.oskitcompose.resources.KMPResource

actual object Resources {
    actual val Settings: KMPResource = KMPResource.Desktop("images/settings.svg")
    actual val WifiTethering: KMPResource = KMPResource.Desktop("images/wifi-tethering.svg")
    actual val DeviceDesktopLinux: KMPResource = KMPResource.Desktop("images/device-desktop-linux.svg")
    actual val DeviceDesktopWindows: KMPResource = KMPResource.Desktop("images/device-desktop-windows.svg")
    actual val DeviceDesktopMacOS: KMPResource = KMPResource.Desktop("images/device-desktop-macos.svg")
    actual val DeviceMobileIOS: KMPResource = KMPResource.Desktop("images/device-mobile-ios.svg")
    actual val DeviceMobileAndroid: KMPResource = KMPResource.Desktop("images/device-mobile-android.svg")
    actual val DeviceUnknown: KMPResource = KMPResource.Desktop("images/device-unknown.svg")
}