package com.ethossoftworks.land.common.ui.home

import com.ethossoftworks.land.common.interactor.discovery.DiscoveryInteractor
import com.ethossoftworks.land.common.interactor.filetransfer.FileTransferInteractor
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.launch

data class AddDeviceModalViewState(
    val ipAddress: String = "",
    val connectionState: ManualDeviceConnectionState = ManualDeviceConnectionState.Idle,
)

enum class ManualDeviceConnectionState {
    Idle,
    Connecting,
    Error;

    override fun toString() = when (this) {
        Idle -> ""
        Connecting -> "Connecting..."
        Error -> "Error connecting to device"
    }
}

class AddDeviceModalViewInteractor(
    private val discoveryInteractor: DiscoveryInteractor,
    private val fileTransferInteractor: FileTransferInteractor,
    private val onDismissRequest: () -> Unit,
): Interactor<AddDeviceModalViewState>(
    initialState = AddDeviceModalViewState(),
    dependencies = listOf(
        discoveryInteractor,
        fileTransferInteractor,
    ),
) {

    fun onAddClicked() {
        interactorScope.launch {
            if (state.ipAddress.isEmpty()) {
                update { state -> state.copy(connectionState = ManualDeviceConnectionState.Error) }
                return@launch
            }

            update { state -> state.copy(connectionState = ManualDeviceConnectionState.Connecting) }
            val connectionOutcome = fileTransferInteractor.testConnection(state.ipAddress)
            if (connectionOutcome !is Outcome.Ok) {
                update { state -> state.copy(connectionState = ManualDeviceConnectionState.Error) }
                return@launch
            }

            discoveryInteractor.addManualDevice(state.ipAddress)
            update { state -> state.copy(connectionState = ManualDeviceConnectionState.Idle) }
            onDismissRequest()
        }
    }

    fun onIpAddressChanged(value: String) {
        update { state -> state.copy(ipAddress = value) }
    }

    fun onCancelled() {
        update { state ->
            state.copy(
                ipAddress = "",
                connectionState = ManualDeviceConnectionState.Idle,
            )
        }
        onDismissRequest()
    }
}