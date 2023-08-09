package com.ethossoftworks.land.common.resources

import com.outsidesource.oskitcompose.resources.KMPResource

expect object Resources {
    val Settings: KMPResource
    val Add: KMPResource
    val Info: KMPResource
    val Cancel: KMPResource
    val WifiTethering: KMPResource
    val DeviceDesktopLinux: KMPResource
    val DeviceDesktopWindows: KMPResource
    val DeviceDesktopMacOS: KMPResource
    val DeviceMobileIOS: KMPResource
    val DeviceMobileAndroid: KMPResource
    val DeviceUnknown: KMPResource
}
