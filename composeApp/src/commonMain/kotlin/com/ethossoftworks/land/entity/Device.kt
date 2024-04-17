package com.ethossoftworks.land.entity

import androidx.compose.runtime.Immutable
import com.outsidesource.oskitkmp.lib.Platform

@Immutable
data class Device(
    val name: String,
    val platform: DevicePlatform,
    val ipAddress: String,
    val port: Int,
)

sealed class DevicePlatform {
    data object iOS: DevicePlatform()
    data object Android: DevicePlatform()
    data object MacOS: DevicePlatform()
    data object Windows: DevicePlatform()
    data object Linux: DevicePlatform()
    data object Unknown: DevicePlatform()
}

fun Platform.toDevicePlatform() = when(this) {
    Platform.Windows -> DevicePlatform.Windows
    Platform.MacOS -> DevicePlatform.MacOS
    Platform.Linux -> DevicePlatform.Linux
    Platform.Android -> DevicePlatform.Android
    Platform.IOS -> DevicePlatform.iOS
    Platform.Unknown -> DevicePlatform.Unknown
}