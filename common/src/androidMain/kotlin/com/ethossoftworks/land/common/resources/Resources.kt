package com.ethossoftworks.land.common.resources

import com.ethossoftworks.land.R
import com.outsidesource.oskitcompose.resources.KMPResource

actual object Resources {
    actual val Settings: KMPResource = KMPResource.Android(R.drawable.settings)
    actual val Add: KMPResource = KMPResource.Android(R.drawable.add)
    actual val Info: KMPResource = KMPResource.Android(R.drawable.info)
    actual val Cancel: KMPResource = KMPResource.Android(R.drawable.cancel)
    actual val WifiTethering: KMPResource = KMPResource.Android(R.drawable.wifi_tethering)
    actual val DeviceDesktopLinux: KMPResource = KMPResource.Android(R.drawable.device_desktop_linux)
    actual val DeviceDesktopWindows: KMPResource = KMPResource.Android(R.drawable.device_desktop_windows)
    actual val DeviceDesktopMacOS: KMPResource = KMPResource.Android(R.drawable.device_desktop_macos)
    actual val DeviceMobileIOS: KMPResource = KMPResource.Android(R.drawable.device_mobile_ios)
    actual val DeviceMobileAndroid: KMPResource = KMPResource.Android(R.drawable.device_mobile_android)
    actual val DeviceUnknown: KMPResource = KMPResource.Android(R.drawable.device_unknown)
}