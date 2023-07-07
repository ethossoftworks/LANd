package com.ethossoftworks.land.common.model.device

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