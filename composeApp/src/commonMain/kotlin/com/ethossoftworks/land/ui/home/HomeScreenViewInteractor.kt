package com.ethossoftworks.land.ui.home

import com.ethossoftworks.land.entity.Device
import com.ethossoftworks.land.interactor.discovery.DiscoveryInteractor
import com.ethossoftworks.land.interactor.filetransfer.FileTransfer
import com.ethossoftworks.land.interactor.filetransfer.FileTransferInteractor
import com.ethossoftworks.land.interactor.preferences.AppPreferencesInteractor
import com.ethossoftworks.land.lib.SystemScreenOpener
import com.ethossoftworks.land.service.discovery.NSDServiceError
import com.ethossoftworks.land.service.filetransfer.FileTransferRequest
import com.ethossoftworks.land.service.preferences.DeviceVisibility
import com.outsidesource.oskitkmp.file.IKMPFileHandler
import com.outsidesource.oskitkmp.file.source
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.Outcome
import com.outsidesource.oskitkmp.outcome.runOnError
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import kotlinx.coroutines.launch

data class HomeViewState(
    val isSettingsBottomSheetVisible: Boolean = false,
    val discoveredDevices: Map<String, Device> = emptyMap(),
    val hasInitialized: Boolean = false,
    val hasSaveFolder: Boolean = false,
    val displayName: String = "",
    val broadcastIp: String = "",
    val activeRequests: Map<Short, FileTransfer> = emptyMap(),
    val transferMessageQueue: List<FileTransfer> = emptyList(),
    val isAddDeviceModalVisible: Boolean = false,
    val isAboutModalVisible: Boolean = false,
    val discoveryError: NSDServiceError? = null,
    val hasBroadcastingError: Boolean = false,
    val hasServerError: Boolean = false,
    val visibility: DeviceVisibility = DeviceVisibility.Visible,
)

class HomeScreenViewInteractor(
    private val appPreferencesInteractor: AppPreferencesInteractor,
    private val discoveryInteractor: DiscoveryInteractor,
    private val fileTransferInteractor: FileTransferInteractor,
    private val fileHandler: IKMPFileHandler,
): Interactor<HomeViewState>(
    initialState = HomeViewState(),
    dependencies = listOf(discoveryInteractor, appPreferencesInteractor, fileTransferInteractor)
) {
    override fun computed(state: HomeViewState): HomeViewState {
        return state.copy(
            discoveredDevices = discoveryInteractor.state.discoveredDevices,
            hasSaveFolder = appPreferencesInteractor.state.saveFolder != null,
            displayName = appPreferencesInteractor.state.displayName,
            activeRequests = fileTransferInteractor.state.activeTransfers,
            transferMessageQueue = fileTransferInteractor.state.transferMessageQueue,
            broadcastIp = discoveryInteractor.state.broadcastIp,
            discoveryError = discoveryInteractor.state.discoveryError,
            hasBroadcastingError = discoveryInteractor.state.hasBroadcastingError,
            hasServerError = fileTransferInteractor.state.hasServerError,
            visibility = appPreferencesInteractor.state.deviceVisibility,
        )
    }

    fun viewMounted() {
        interactorScope.launch {
            appPreferencesInteractor.awaitInitialization()

            startDiscovery()

            if (appPreferencesInteractor.state.deviceVisibility != DeviceVisibility.SendOnly) {
                fileTransferInteractor.startServer()
            }

            update { state -> state.copy(hasInitialized = true) }
        }
    }

    fun onSelectSaveFolderClicked() {
        interactorScope.launch {
            val folderOutcome = fileHandler.pickDirectory()
            if (folderOutcome !is Outcome.Ok) return@launch
            val folder = folderOutcome.value ?: return@launch
            appPreferencesInteractor.setSaveFolder(folder)
        }
    }

    fun onDeviceClicked(device: Device) {
        interactorScope.launch onDeviceClickedLaunch@{
            val files = fileHandler
                .pickFiles()
                .unwrapOrReturn { return@onDeviceClickedLaunch } ?: return@onDeviceClickedLaunch

            files.forEach { file ->
                launch {
                    val metadata = fileHandler.readMetadata(file).unwrapOrReturn { return@launch }
                    val source = file.source().unwrapOrReturn { return@launch }

                    val fileTransfer = FileTransferRequest(
                        senderName = appPreferencesInteractor.state.displayName,
                        fileName = file.name,
                        length = metadata.size,
                        source = source,
                    )

                    fileTransferInteractor.sendFile(device, fileTransfer)
                }
            }
        }
    }

    fun onRestartDiscoveryClicked() {
        interactorScope.launch { startDiscovery() }
    }

    private suspend fun startDiscovery() {
        if (!discoveryInteractor.state.isBroadcasting &&
            appPreferencesInteractor.state.deviceVisibility == DeviceVisibility.Visible
        ) {
            discoveryInteractor.startServiceBroadcasting(appPreferencesInteractor.state.displayName)
        }

        discoveryInteractor.startDeviceDiscovery()
    }

    fun onRestartServerClicked() {
        interactorScope.launch { fileTransferInteractor.startServer() }
    }

    fun onOpenSettingsClicked() {
        SystemScreenOpener.openSettings()
    }

    fun onSettingsButtonClicked() {
        update { state -> state.copy(isSettingsBottomSheetVisible = true) }
    }

    fun onInfoButtonClicked() {
        update { state -> state.copy(isAboutModalVisible = true) }
    }

    fun onAboutModalDismissed() {
        update { state -> state.copy(isAboutModalVisible = false) }
    }

    fun onAddButtonClicked() {
        update { state -> state.copy(isAddDeviceModalVisible = true) }
    }

    fun onAddDeviceModalDismissed() {
        update { state -> state.copy(isAddDeviceModalVisible = false) }
    }

    fun onSettingsBottomSheetDismissed() {
        update { state -> state.copy(isSettingsBottomSheetVisible = false) }
    }
}