package com.ethossoftworks.land.common.ui.home

import com.ethossoftworks.land.common.coordinator.AppCoordinator
import com.ethossoftworks.land.common.interactor.discovery.DiscoveryInteractor
import com.ethossoftworks.land.common.interactor.filetransfer.FileTransferInteractor
import com.ethossoftworks.land.common.interactor.preferences.AppPreferencesInteractor
import com.ethossoftworks.land.common.model.device.Device
import com.ethossoftworks.land.common.service.file.IFileHandler
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.launch

data class HomeViewState(
    val discoveredDevices: Map<String, Device> = emptyMap(),
    val hasSaveFolder: Boolean = false,
    val displayName: String = "",
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
        )
    }

    fun viewMounted() {
        interactorScope.launch {
            appPreferencesInteractor.awaitInitialization()
            fileTransferInteractor.startServer()
            discoveryInteractor.startDeviceDiscovery()
            discoveryInteractor.startServiceBroadcasting(appPreferencesInteractor.state.displayName)

            // TODO: Need to stop broadcasting if the server has stopped
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
            val saveFolder = appPreferencesInteractor.state.saveFolder ?: return@launch

            val metadataOutcome = fileHandler.readFileMetadata(saveFolder, file)
            if (metadataOutcome !is Outcome.Ok) return@launch

//            fileTransferInteractor.sendFile(device)
        }
    }
}