package com.ethossoftworks.land.common.ui.home

import com.ethossoftworks.land.common.coordinator.AppCoordinator
import com.ethossoftworks.land.common.interactor.discovery.DiscoveryInteractor
import com.ethossoftworks.land.common.interactor.filetransfer.FileTransfer
import com.ethossoftworks.land.common.interactor.filetransfer.FileTransferInteractor
import com.ethossoftworks.land.common.interactor.preferences.AppPreferencesInteractor
import com.ethossoftworks.land.common.model.device.Device
import com.ethossoftworks.land.common.service.file.FileWriteMode
import com.ethossoftworks.land.common.service.file.IFileHandler
import com.ethossoftworks.land.common.service.filetransfer.FileTransferRequest
import com.ethossoftworks.land.common.service.filetransfer.FileTransferResponseType
import com.ethossoftworks.land.common.service.preferences.DeviceVisibility
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.Outcome
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
)

class HomeScreenViewInteractor(
    private val appPreferencesInteractor: AppPreferencesInteractor,
    private val discoveryInteractor: DiscoveryInteractor,
    private val fileTransferInteractor: FileTransferInteractor,
    private val fileHandler: IFileHandler,
    private val appCoordinator: AppCoordinator,
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
            broadcastIp = discoveryInteractor.state.broadcastIp
        )
    }

    fun viewMounted() {
        interactorScope.launch {
            appPreferencesInteractor.awaitInitialization()

            discoveryInteractor.startDeviceDiscovery()

            if (appPreferencesInteractor.state.deviceVisibility != DeviceVisibility.SendOnly) {
                fileTransferInteractor.startServer()
            }

            if (!discoveryInteractor.state.isBroadcasting &&
                appPreferencesInteractor.state.deviceVisibility == DeviceVisibility.Visible
            ) {
                discoveryInteractor.startServiceBroadcasting(appPreferencesInteractor.state.displayName)
            }

            update { state -> state.copy(hasInitialized = true) }
            // TODO: Need to stop broadcasting if the file transfer server has stopped
        }
    }

    fun onSelectSaveFolderClicked() {
        interactorScope.launch {
            val folderOutcome = fileHandler.selectFolder()
            if (folderOutcome !is Outcome.Ok) return@launch
            val folder = folderOutcome.value ?: return@launch

            val saveOutcome = appPreferencesInteractor.setSaveFolder(folder)
            if (saveOutcome is Outcome.Error) {
                // TODO: Handle error
            }
        }
    }

    fun onDeviceClicked(device: Device) {
        interactorScope.launch {
            val fileOutcome = fileHandler.selectFile()
            if (fileOutcome !is Outcome.Ok) return@launch

            // TODO: Handle providing the user details for early returns
            val file = fileOutcome.value ?: return@launch

            val metadataOutcome = fileHandler.readFileMetadata(file)
            if (metadataOutcome !is Outcome.Ok) return@launch

            val sourceOutcome = fileHandler.openFileToRead(file)
            if (sourceOutcome !is Outcome.Ok) return@launch

            val fileTransfer = FileTransferRequest(
                senderName = appPreferencesInteractor.state.displayName,
                fileName = metadataOutcome.value.name,
                length = metadataOutcome.value.length,
                source = sourceOutcome.value,
            )

            fileTransferInteractor.sendFile(device, fileTransfer)
        }
    }

    fun respondToRequest(
        request: FileTransfer,
        response: FileTransferResponseType,
        mode: FileWriteMode = FileWriteMode.Overwrite,
    ) {
        interactorScope.launch {
            fileTransferInteractor.respondToTransferRequest(request, response, mode)
        }
    }

    fun transferMessageQueueItemHandled(item: FileTransfer) {
        fileTransferInteractor.transferMessageQueueItemHandled(item)
    }

    fun onSettingsButtonClicked() {
        update { state -> state.copy(isSettingsBottomSheetVisible = true) }
    }

    fun onInfoButtonClicked() {

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