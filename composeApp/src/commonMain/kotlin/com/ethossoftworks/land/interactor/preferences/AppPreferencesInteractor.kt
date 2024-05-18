package com.ethossoftworks.land.interactor.preferences

import com.ethossoftworks.land.entity.Contact
import com.ethossoftworks.land.service.preferences.DeviceVisibility
import com.ethossoftworks.land.service.preferences.IPreferencesService
import com.ethossoftworks.land.service.preferences.TransferRequestPermissionType
import com.outsidesource.oskitkmp.file.KMPFileHandler
import com.outsidesource.oskitkmp.file.KMPFileRef
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current
import com.outsidesource.oskitkmp.outcome.Outcome
import com.outsidesource.oskitkmp.outcome.runOnOk
import com.outsidesource.oskitkmp.outcome.unwrapOrDefault
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

data class AppPreferencesState(
    val displayName: String = "",
    val saveFolder: KMPFileRef? = null,
    val contacts: Map<String, Contact> = emptyMap(),
    val transferRequestPermissionType: TransferRequestPermissionType = TransferRequestPermissionType.AskAll,
    val deviceVisibility: DeviceVisibility = DeviceVisibility.Visible,
    val useEncryption: Boolean = false,
)

class AppPreferencesInteractor(
    private val preferencesService: IPreferencesService,
    private val fileHandler: KMPFileHandler,
): Interactor<AppPreferencesState>(
    initialState = AppPreferencesState(),
) {
    private val hasInitialized = CompletableDeferred<Unit>()

    init {
        interactorScope.launch {
            when (val displayName = preferencesService.getDisplayName()) {
                is Outcome.Ok -> update { state -> state.copy(displayName = displayName.value) }
                else -> {
                    val platform = when (Platform.current) {
                        Platform.MacOS -> "MacOS"
                        Platform.Windows -> "Windows"
                        Platform.Linux -> "Linux"
                        Platform.Android -> "Android"
                        Platform.IOS -> "iOS"
                        Platform.Unknown -> "Unknown"
                    }
                    val defaultName = "$platform ${(Random.nextFloat() * 10_000).roundToInt()}"
                    setDisplayName(defaultName)
                    update { state -> state.copy(displayName = defaultName) }
                }
            }

            val saveFolder = preferencesService.getSaveFolder().unwrapOrDefault(null)?.let {
                if (!fileHandler.exists(it)) return@let null
                it
            }
//            val contacts = preferencesService.getContacts().getOrElse(emptyMap())
            val deviceVisibility = preferencesService.getVisibility().unwrapOrDefault(DeviceVisibility.Visible)
            val requestPermissionType = preferencesService.getTransferRequestPermission().unwrapOrDefault(
                TransferRequestPermissionType.AskAll)
            val useEncryption = preferencesService.getUseEncryption().unwrapOrDefault(false)

            update { state ->
                state.copy(
//                    contacts = contacts,
                    useEncryption = useEncryption,
                    saveFolder = saveFolder,
                    deviceVisibility = deviceVisibility,
                    transferRequestPermissionType = requestPermissionType,
                )
            }

            hasInitialized.complete(Unit)
        }
    }

    suspend fun awaitInitialization() {
        hasInitialized.join()
    }

    suspend fun setSaveFolder(folder: KMPFileRef): Outcome<Unit, Any> {
        val outcome = preferencesService.setSaveFolder(folder)
        if (outcome is Outcome.Ok) update { state -> state.copy(saveFolder = folder) }
        return outcome
    }

    suspend fun setDisplayName(name: String): Outcome<Unit, Any> {
        val outcome = preferencesService.setDisplayName(name)
        if (outcome is Outcome.Ok) update { state -> state.copy(displayName = name) }
        return outcome
    }

    suspend fun setTransferRequestPermission(type: TransferRequestPermissionType): Outcome<Unit, Any> {
        val outcome = preferencesService.setTransferRequestPermission(type)
        if (outcome is Outcome.Ok) update { state -> state.copy(transferRequestPermissionType = type) }
        return outcome
    }

    suspend fun setDeviceVisibility(visibility: DeviceVisibility): Outcome<Unit, Any> {
        val outcome = preferencesService.setVisibility(visibility)
        if (outcome is Outcome.Ok) update { state -> state.copy(deviceVisibility = visibility) }
        return outcome
    }

    suspend fun setUseEncryption(value: Boolean): Outcome<Unit, Any> {
        val outcome = preferencesService.setUseEncryption(value)
        return outcome.runOnOk { update { state -> state.copy(useEncryption = value) } }
    }

    suspend fun addContact(contact: Contact): Outcome<Unit, Any> {
        val outcome = preferencesService.addContact(contact)
        if (outcome !is Outcome.Ok) return outcome

        update { state ->
            state.copy(contacts = state.contacts.toMutableMap().apply { put(contact.name, contact) })
        }

        return outcome
    }

    suspend fun removeContact(contact: Contact): Outcome<Unit, Any> {
        val outcome = preferencesService.removeContact(contact)
        if (outcome !is Outcome.Ok) return outcome

        update { state ->
            state.copy(contacts = state.contacts.toMutableMap().apply { remove(contact.name) })
        }

        return outcome
    }
}