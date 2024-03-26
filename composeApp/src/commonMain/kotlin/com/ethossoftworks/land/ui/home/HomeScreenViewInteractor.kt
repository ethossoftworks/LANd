package com.ethossoftworks.land.ui.home

import com.ethossoftworks.land.coordinator.AppCoordinator
import com.ethossoftworks.land.interactor.discovery.DiscoveryInteractor
import com.ethossoftworks.land.interactor.filetransfer.FileTransfer
import com.ethossoftworks.land.interactor.filetransfer.FileTransferInteractor
import com.ethossoftworks.land.interactor.preferences.AppPreferencesInteractor
import com.ethossoftworks.land.entity.Device
import com.ethossoftworks.land.service.filetransfer.FileTransferRequest
import com.ethossoftworks.land.service.preferences.DeviceVisibility
import com.outsidesource.oskitkmp.file.IKMPFileHandler
import com.outsidesource.oskitkmp.file.source
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.Outcome
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
)

class HomeScreenViewInteractor(
    private val appPreferencesInteractor: AppPreferencesInteractor,
    private val discoveryInteractor: DiscoveryInteractor,
    private val fileTransferInteractor: FileTransferInteractor,
    private val fileHandler: IKMPFileHandler,
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
            val folderOutcome = fileHandler.pickDirectory()
            if (folderOutcome !is Outcome.Ok) return@launch
            val folder = folderOutcome.value ?: return@launch

            val saveOutcome = appPreferencesInteractor.setSaveFolder(folder)
            if (saveOutcome is Outcome.Error) {
                // TODO: Handle error
            }
        }
    }

    fun onDeviceClicked(device: Device) {
        interactorScope.launch onDeviceClickedLaunch@{
            val files = fileHandler.pickFiles().unwrapOrReturn { return@onDeviceClickedLaunch }

            // TODO: Handle providing the user details for early returns
            if (files == null) return@onDeviceClickedLaunch

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