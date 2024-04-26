package com.ethossoftworks.land.interactor.discovery

import com.ethossoftworks.land.entity.Device
import com.ethossoftworks.land.entity.DevicePlatform
import com.ethossoftworks.land.entity.toDevicePlatform
import com.ethossoftworks.land.lib.bytes.toUShort
import com.ethossoftworks.land.service.discovery.INSDService
import com.ethossoftworks.land.service.discovery.NSDService
import com.ethossoftworks.land.service.discovery.NSDServiceError
import com.ethossoftworks.land.service.discovery.NSDServiceEvent
import com.ethossoftworks.land.service.filetransfer.FILE_TRANSFER_PORT
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DiscoveryState(
    val discoveredDevices: Map<String, Device> = emptyMap(),
    val broadcastingDeviceName: String? = null,
    val broadcastIp: String? = null,
    val isBroadcasting: Boolean = false,
    val discoveryError: NSDServiceError? = null,
    val hasBroadcastingError: Boolean = false,
)

const val DNS_SD_PORT = 50076
private const val DISCOVERY_TYPE = "_land._tcp.local."
private const val DISCOVERY_PROP_PLATFORM_KEY = "platform"
private const val DISCOVERY_PROP_PORT_KEY = "port"

class DiscoveryInteractor(
    private val discoveryService: INSDService,
): Interactor<DiscoveryState>(
    initialState = DiscoveryState(),
) {
    private val discoveryJob = atomic<Job?>(null)

    init {
        interactorScope.launch {
            discoveryService.init()
        }
        startIpCheck()
    }

    private fun startIpCheck() {
        interactorScope.launch {
            while (isActive) {
                val ip = getLocalIpAddress()
                update { state -> state.copy(broadcastIp = ip) }
                delay(30_000)
            }
        }
    }

    fun addManualDevice(device: Device) {
        update { state ->
            state.copy(
                discoveredDevices = state.discoveredDevices.toMutableMap().apply {
                    put(device.ipAddress, device)
                }
            )
        }
    }

    fun startDeviceDiscovery() {
        discoveryJob.value?.cancel()

        interactorScope.launch {
            update { state -> state.copy(discoveryError = null) }

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
                    is NSDServiceEvent.Error -> {
                        update { state -> state.copy(discoveryError = it.error) }
                        stopServiceBroadcasting()
                    }
                    else -> {}
                }
            }
        }.apply { discoveryJob.update { this } }
    }

    fun addUnknownDevice(device: Device) {
        update { state ->
            state.copy(
                discoveredDevices = state.discoveredDevices.toMutableMap().apply { put(device.name, device) }
            )
        }
    }

    suspend fun startServiceBroadcasting(name: String): Outcome<Unit, Any> {
        update { state -> state.copy(broadcastingDeviceName = name) }

        val outcome = discoveryService.registerService(
            type = DISCOVERY_TYPE,
            name = name,
            port = DNS_SD_PORT,
            properties = mapOf(
                DISCOVERY_PROP_PLATFORM_KEY to Platform.current.toDevicePlatform().toDiscoveryString(),
                DISCOVERY_PROP_PORT_KEY to byteArrayOf(
                    ((FILE_TRANSFER_PORT shr 8) and 0xFF).toByte(),
                    (FILE_TRANSFER_PORT and 0xFF).toByte()
                ),
            )
        )

        if (outcome is Outcome.Ok) {
            update { state -> state.copy(isBroadcasting = true, hasBroadcastingError = false) }
        } else {
            update { state ->
                state.copy(
                    isBroadcasting = false,
                    broadcastingDeviceName = null,
                    hasBroadcastingError = true,
                )
            }
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

    private suspend fun getLocalIpAddress(): String? {
        return discoveryService.getLocalIpAddress()
    }
}

private fun NSDService.toDevice() = Device(
    name = name,
    platform = props[DISCOVERY_PROP_PLATFORM_KEY]?.decodeToString()?.toPlatform() ?: DevicePlatform.Unknown,
    port = props[DISCOVERY_PROP_PORT_KEY]?.toUShort()?.toInt() ?: 0,
    ipAddress = iPv4Addresses.firstOrNull() ?: "",
)

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