package com.ethossoftworks.land.common.entity

import androidx.compose.runtime.Immutable
import com.outsidesource.oskitkmp.lib.Platform

@Immutable
data class Device(
    val name: String,
    val platform: DevicePlatform,
    val ipAddress: String,
)

sealed class DevicePlatform {
    object iOS: DevicePlatform()
    object Android: DevicePlatform()
    object MacOS: DevicePlatform()
    object Windows: DevicePlatform()
    object Linux: DevicePlatform()
    object Unknown: DevicePlatform()
}

fun Platform.toDevicePlatform() = when(this) {
    Platform.Windows -> DevicePlatform.Windows
    Platform.MacOS -> DevicePlatform.MacOS
    Platform.Linux -> DevicePlatform.Linux
    Platform.Android -> DevicePlatform.Android
    Platform.IOS -> DevicePlatform.iOS
    Platform.Unknown -> DevicePlatform.Unknown
}