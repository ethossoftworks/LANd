package com.ethossoftworks.land.ui.home

import com.ethossoftworks.land.interactor.discovery.DiscoveryInteractor
import com.ethossoftworks.land.interactor.filetransfer.FileTransferInteractor
import com.ethossoftworks.land.interactor.preferences.AppPreferencesInteractor
import com.ethossoftworks.land.entity.Contact
import com.ethossoftworks.land.service.preferences.DeviceVisibility
import com.ethossoftworks.land.service.preferences.TransferRequestPermissionType
import com.outsidesource.oskitkmp.file.IKMPFileHandler
import com.outsidesource.oskitkmp.file.KMPFileRef
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.launch

data class SettingsBottomSheetState(
    val saveFolder: KMPFileRef? = null,
    val saveFolderExists: Boolean = false,
    val displayName: String = "",
    val contacts: Map<String, Contact> = emptyMap(),
    val transferRequestPermissionType: TransferRequestPermissionType = TransferRequestPermissionType.AskAll,
    val deviceVisibility: DeviceVisibility = DeviceVisibility.Visible,
    val contactToAdd: String = "",
    val editableDisplayName: String = "",
    val isEditingDisplayName: Boolean = false,
)

class SettingsBottomSheetViewInteractor(
    private val preferencesInteractor: AppPreferencesInteractor,
    private val discoveryInteractor: DiscoveryInteractor,
    private val fileTransferInteractor: FileTransferInteractor,
    private val fileHandler: IKMPFileHandler,
) : Interactor<SettingsBottomSheetState>(
    initialState = SettingsBottomSheetState(
        editableDisplayName = preferencesInteractor.state.displayName
    ),
    dependencies = listOf(preferencesInteractor),
) {
    override fun computed(state: SettingsBottomSheetState): SettingsBottomSheetState {
        return state.copy(
            saveFolder = preferencesInteractor.state.saveFolder,
            displayName = preferencesInteractor.state.displayName,
            contacts = preferencesInteractor.state.contacts,
            transferRequestPermissionType = preferencesInteractor.state.transferRequestPermissionType,
            deviceVisibility = preferencesInteractor.state.deviceVisibility,
        )
    }

    fun onSaveFolderChangeClicked() {
        interactorScope.launch {
            val folderOutcome = fileHandler.pickDirectory(startingDir = state.saveFolder)
            if (folderOutcome !is Outcome.Ok) return@launch

            val folder = folderOutcome.value ?: return@launch
            preferencesInteractor.setSaveFolder(folder)
        }
    }

    fun onDeviceVisibilityChanged(value: DeviceVisibility) {
        interactorScope.launch {
            val visibilityOutcome = preferencesInteractor.setDeviceVisibility(value)
            if (visibilityOutcome !is Outcome.Ok) return@launch

            when (value) {
                DeviceVisibility.Visible -> {
                    if (!discoveryInteractor.state.isBroadcasting) {
                        discoveryInteractor.startServiceBroadcasting(state.displayName)
                    }

                    if (!fileTransferInteractor.state.isServerRunning) {
                        fileTransferInteractor.startServer()
                    }
                }
                DeviceVisibility.Hidden -> {
                    if (discoveryInteractor.state.isBroadcasting) {
                        discoveryInteractor.stopServiceBroadcasting()
                    }

                    if (!fileTransferInteractor.state.isServerRunning) {
                        fileTransferInteractor.startServer()
                    }
                }
                DeviceVisibility.SendOnly -> {
                    if (discoveryInteractor.state.isBroadcasting) {
                        discoveryInteractor.stopServiceBroadcasting()
                    }

                    if (fileTransferInteractor.state.isServerRunning) {
                        fileTransferInteractor.stopServer()
                    }
                }
            }
        }
    }

    fun onTransferRequestPermissionTypeChanged(value: TransferRequestPermissionType) {
        interactorScope.launch {
            preferencesInteractor.setTransferRequestPermission(value)
        }
    }

    fun onContactToAddChanged(value: String) {
        update { state -> state.copy(contactToAdd = value) }
    }

    fun onAddContactClicked() {
        interactorScope.launch {
            val contactOutcome = preferencesInteractor.addContact(Contact(state.contactToAdd, null))
            if (contactOutcome !is Outcome.Ok) return@launch

            update { state -> state.copy(contactToAdd = "") }
        }
    }

    fun onDeleteContactClicked(contact: Contact) {
        interactorScope.launch {
            val contactOutcome = preferencesInteractor.removeContact(contact)
            if (contactOutcome !is Outcome.Ok) return@launch
        }
    }

    fun onChangeDisplayNameClicked() {
        interactorScope.launch {
            if (state.isEditingDisplayName) {
                val displayNameOutcome = preferencesInteractor.setDisplayName(state.editableDisplayName)
                if (displayNameOutcome !is Outcome.Ok) return@launch

                update { state -> state.copy(isEditingDisplayName = false) }

                discoveryInteractor.stopServiceBroadcasting()
                discoveryInteractor.startServiceBroadcasting(state.displayName)
            } else {
                update { state -> state.copy(isEditingDisplayName = true) }
            }
        }
    }

    fun onCancelDisplayNameClicked() {
        update { state ->
            state.copy(
                editableDisplayName = state.displayName,
                isEditingDisplayName = false,
            )
        }
    }

    fun onEditableDisplayNameChanged(value: String) {
        update { state -> state.copy(editableDisplayName = value) }
    }
}