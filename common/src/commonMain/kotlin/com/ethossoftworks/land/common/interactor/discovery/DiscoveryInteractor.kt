package com.ethossoftworks.land.common.interactor.discovery

import com.ethossoftworks.land.common.model.device.Device
import com.ethossoftworks.land.common.model.device.DevicePlatform
import com.ethossoftworks.land.common.service.discovery.INSDService
import com.ethossoftworks.land.common.service.discovery.NSDService
import com.ethossoftworks.land.common.service.discovery.NSDServiceEvent
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.launch
import okio.internal.commonToUtf8String
import kotlin.math.roundToInt

data class DiscoveryState(
    val discoveredDevices: Map<String, Device> = emptyMap(),
    val broadcastingDeviceName: String? = null,
)

private const val DISCOVERY_TYPE = "_land._tcp.local."
private const val DISCOVERY_PROP_PLATFORM_KEY = "platform"
private const val DISCOVERY_PORT = 7788

class DiscoveryInteractor(
    private val discoveryService: INSDService,
): Interactor<DiscoveryState>(
    initialState = DiscoveryState()
) {
    init {
        interactorScope.launch {
            discoveryService.init()
        }
    }

    suspend fun discoverDevices() {
        discoveryService.observeServices(DISCOVERY_TYPE).collect {
            when (it) {
                is NSDServiceEvent.ServiceResolved -> {
                    if (it.service.iPv4Addresses.firstOrNull() == null) return@collect
                    if (it.service.name == state.broadcastingDeviceName) return@collect

                    update { state ->
                        state.copy(
                            discoveredDevices = state.discoveredDevices.toMutableMap().apply {
                                put(it.service.name, it.service.toDevice())
                            }
                        )
                    }
                }
                is NSDServiceEvent.ServiceRemoved -> {
                    update { state ->
                        state.copy(
                            discoveredDevices = state.discoveredDevices.toMutableMap().apply {
                                remove(it.service.name)
                            }
                        )
                    }
                }
                else -> {}
            }
        }
    }

    suspend fun startBroadcasting(): Outcome<Unit, Exception> {
        val name = (Math.random() * 10_000).roundToInt().toString()

        update { state -> state.copy(broadcastingDeviceName = name) }

        val outcome = discoveryService.registerService(
            type = DISCOVERY_TYPE,
            name = name,
            port = DISCOVERY_PORT,
            properties = mapOf(DISCOVERY_PROP_PLATFORM_KEY to Platform.current.toDevicePlatform().toDiscoveryString())
        )

        if (outcome is Outcome.Ok) {
            update { state -> state.copy(broadcastingDeviceName = name) }
        } else {
            update { state -> state.copy(broadcastingDeviceName = null) }
        }

        return outcome
    }

    suspend fun stopBroadcasting() {
        val broadcastingDeviceName = state.broadcastingDeviceName ?: return

        update { state -> state.copy(broadcastingDeviceName = null) }

        return discoveryService.unregisterService(
            type = DISCOVERY_TYPE,
            name = broadcastingDeviceName,
            port = DISCOVERY_PORT,
        )
    }
}

private fun NSDService.toDevice() = Device(
    name = name,
    platform = props[DISCOVERY_PROP_PLATFORM_KEY]?.decodeToString()?.toPlatform() ?: DevicePlatform.Unknown,
    ipAddress = iPv4Addresses.firstOrNull() ?: ""
)

private fun Platform.toDevicePlatform() = when(this) {
    Platform.Windows -> DevicePlatform.Windows
    Platform.MacOS -> DevicePlatform.MacOS
    Platform.Linux -> DevicePlatform.Linux
    Platform.Android -> DevicePlatform.Android
    Platform.iOS -> DevicePlatform.iOS
    Platform.Unknown -> DevicePlatform.Unknown
}

private fun String.toPlatform() = when(this) {
    "windows" -> DevicePlatform.Windows
    "macos" -> DevicePlatform.MacOS
    "linux" -> DevicePlatform.Linux
    "ios" -> DevicePlatform.iOS
    "android" -> DevicePlatform.Android
    else -> DevicePlatform.Unknown
}

private fun DevicePlatform.toDiscoveryString() = when(this) {
    DevicePlatform.Windows -> "windows"
    DevicePlatform.MacOS -> "macos"
    DevicePlatform.Linux -> "linux"
    DevicePlatform.iOS -> "ios"
    DevicePlatform.Android -> "android"
    DevicePlatform.Unknown -> "unknown"
}