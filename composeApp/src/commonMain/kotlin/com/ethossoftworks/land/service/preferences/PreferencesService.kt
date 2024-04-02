package com.ethossoftworks.land.service.preferences

import com.ethossoftworks.land.entity.Contact
import com.outsidesource.oskitkmp.file.KMPFileRef
import com.outsidesource.oskitkmp.outcome.Outcome
import com.outsidesource.oskitkmp.outcome.unwrapOrNull
import com.outsidesource.oskitkmp.storage.IKMPStorage
import com.outsidesource.oskitkmp.storage.InMemoryKMPStorageNode
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SAVE_FOLDER_KEY = "saveFolder"
private const val DISPLAY_NAME_KEY = "displayName"
private const val CONTACTS_KEY = "contacts"
private const val TRANSFER_REQUEST_PERMISSION_KEY = "transferRequestPermission"
private const val DEVICE_VISIBILITY_KEY = "deviceVisibility"
private val json = Json { ignoreUnknownKeys = true }

class PreferencesService(storage: IKMPStorage): IPreferencesService {
    private val preferences = storage.openNode("preferences").unwrapOrNull() ?: InMemoryKMPStorageNode()

    override suspend fun setSaveFolder(folder: KMPFileRef): Outcome<Unit, Any> {
        return try {
            preferences.putString(SAVE_FOLDER_KEY, folder.toPersistableString())
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun getSaveFolder(): Outcome<KMPFileRef, Any> {
        return try {
            val value = preferences.getString(SAVE_FOLDER_KEY) ?: return Outcome.Error(Unit)
            Outcome.Ok(KMPFileRef.fromPersistableString(value))
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun setDisplayName(name: String): Outcome<Unit, Any> {
        return try {
            preferences.putString(DISPLAY_NAME_KEY, name)
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun getDisplayName(): Outcome<String, Any> {
        return try {
            val value = preferences.getString(DISPLAY_NAME_KEY) ?: return Outcome.Error(Unit)
            Outcome.Ok(value)
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun addContact(contact: Contact): Outcome<Unit, Any> {
        return when (val contacts = getContacts()) {
            is Outcome.Ok -> {
                val updated = contacts.value.toMutableMap().apply { put(contact.name, contact) }
                setContacts(updated)
            }
            is Outcome.Error -> contacts
        }
    }

    override suspend fun removeContact(contact: Contact): Outcome<Unit, Any> {
        return when (val contacts = getContacts()) {
            is Outcome.Ok -> {
                val updated = contacts.value.toMutableMap().apply { remove(contact.name) }
                setContacts(updated)
            }
            is Outcome.Error -> contacts
        }
    }

    private fun setContacts(contacts: Map<String, Contact>): Outcome<Unit, Any> {
        return try {
            preferences.putString(CONTACTS_KEY, json.encodeToString(contacts))
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun getContacts(): Outcome<Map<String, Contact>, Any> {
        return try {
            val contacts = preferences.getString(CONTACTS_KEY) ?: return Outcome.Error(Unit)
            Outcome.Ok(json.decodeFromString(contacts))
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun getVisibility(): Outcome<DeviceVisibility, Any> {
        return try {
            val id = preferences.getInt(DEVICE_VISIBILITY_KEY) ?: DeviceVisibility.Visible.toId()
            Outcome.Ok(id.toDeviceVisibility())
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun setVisibility(visibility: DeviceVisibility): Outcome<Unit, Any> {
        return try {
            preferences.putInt(DEVICE_VISIBILITY_KEY, visibility.toId())
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun getTransferRequestPermission(): Outcome<TransferRequestPermissionType, Any> {
        return try {
            val type = (preferences.getInt(TRANSFER_REQUEST_PERMISSION_KEY) ?: TransferRequestPermissionType.AskAll.toId())
                .toTransferRequestPermissionType()
            Outcome.Ok(type)
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun setTransferRequestPermission(type: TransferRequestPermissionType): Outcome<Unit, Any> {
        return try {
            preferences.putInt(TRANSFER_REQUEST_PERMISSION_KEY, type.toId())
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }
}