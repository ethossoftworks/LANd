package com.ethossoftworks.land.common.interactor.discovery

import com.ethossoftworks.land.common.model.device.Device
import com.ethossoftworks.land.common.model.device.DevicePlatform
import com.ethossoftworks.land.common.service.discovery.INSDService
import com.ethossoftworks.land.common.service.discovery.NSDService
import com.ethossoftworks.land.common.service.discovery.NSDServiceEvent
import com.ethossoftworks.land.common.service.filetransfer.FILE_TRANSFER_PORT
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DiscoveryState(
    val discoveredDevices: Map<String, Device> = emptyMap(),
    val broadcastingDeviceName: String? = null,
    val broadcastIp: String = "",
    val isBroadcasting: Boolean = false,
)

private const val DISCOVERY_TYPE = "_land._tcp.local."
private const val DISCOVERY_PROP_PLATFORM_KEY = "platform"

class DiscoveryInteractor(
    private val discoveryService: INSDService,
): Interactor<DiscoveryState>(
    initialState = DiscoveryState(),
) {
    init {
        interactorScope.launch {
            discoveryService.init()
        }
        startIpCheck()
    }

    private fun startIpCheck() {
        interactorScope.launch {
            while(isActive) {
                val ip = getLocalIpAddress()
                update { state -> state.copy(broadcastIp = ip) }
                delay(30_000)
            }
        }
    }

    fun startDeviceDiscovery() {
        interactorScope.launch {
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
    }

    suspend fun startServiceBroadcasting(name: String): Outcome<Unit, Any> {
        update { state -> state.copy(broadcastingDeviceName = name) }

        val outcome = discoveryService.registerService(
            type = DISCOVERY_TYPE,
            name = name,
            port = FILE_TRANSFER_PORT,
            properties = mapOf(DISCOVERY_PROP_PLATFORM_KEY to Platform.current.toDevicePlatform().toDiscoveryString())
        )

        if (outcome is Outcome.Ok) {
            update { state -> state.copy(isBroadcasting = true) }
        } else {
            update { state -> state.copy(isBroadcasting = false, broadcastingDeviceName = null) }
        }

        return outcome
    }

    suspend fun stopServiceBroadcasting(): Outcome<Unit, Any> {
        val broadcastingDeviceName = state.broadcastingDeviceName ?: return Outcome.Error(Unit)

        update { state -> state.copy(broadcastingDeviceName = null, isBroadcasting = false) }

        return discoveryService.unregisterService(
            type = DISCOVERY_TYPE,
            name = broadcastingDeviceName,
            port = FILE_TRANSFER_PORT,
        )
    }

    private suspend fun getLocalIpAddress(): String {
        return discoveryService.getLocalIpAddress()
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