package com.ethossoftworks.land.service.preferences

import com.ethossoftworks.land.entity.Contact
import com.outsidesource.oskitkmp.file.KMPFileRef
import com.outsidesource.oskitkmp.outcome.Outcome
import com.outsidesource.oskitkmp.outcome.unwrapOrDefault
import com.outsidesource.oskitkmp.outcome.unwrapOrNull
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import com.outsidesource.oskitkmp.storage.IKMPStorage
import com.outsidesource.oskitkmp.storage.IKMPStorageNode
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.prefs.Preferences

private const val SAVE_FOLDER_KEY = "saveFolder"
private const val DISPLAY_NAME_KEY = "displayName"
private const val CONTACTS_KEY = "contacts"
private const val TRANSFER_REQUEST_PERMISSION_KEY = "transferRequestPermission"
private const val DEVICE_VISIBILITY_KEY = "deviceVisibility"
private val json = Json { ignoreUnknownKeys = true }

class PreferencesService(storage: IKMPStorage): IPreferencesService {
    private val preferences = storage.openNode("preferences").unwrapOrReturn { throw Exception("No Storage") }

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

private class InMemoryStorageNode: IKMPStorageNode {
    val storage = atomic(mapOf<String, Any>())

    override fun clear(): Outcome<Unit, Exception> {
        storage.update { it.toMutableMap().apply { clear() } }
        return Outcome.Ok(Unit)
    }

    override fun close() {}

    override fun contains(key: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun dbFileSize(): Long {
        return 0
    }

    override fun getBoolean(key: String): Boolean? {
        TODO("Not yet implemented")
    }

    override fun getBytes(key: String): ByteArray? {
        TODO("Not yet implemented")
    }

    override fun getDouble(key: String): Double? {
        TODO("Not yet implemented")
    }

    override fun getFloat(key: String): Float? {
        TODO("Not yet implemented")
    }

    override fun getInt(key: String): Int? {
        TODO("Not yet implemented")
    }

    override fun getKeys(): List<String>? {
        TODO("Not yet implemented")
    }

    override fun getLong(key: String): Long? {
        TODO("Not yet implemented")
    }

    override fun <T> getSerializable(key: String, deserializer: DeserializationStrategy<T>): T? {
        TODO("Not yet implemented")
    }

    override fun getString(key: String): String? {
        TODO("Not yet implemented")
    }

    override fun keyCount(): Long {
        TODO("Not yet implemented")
    }

    override fun observeBoolean(key: String): Flow<Boolean> {
        TODO("Not yet implemented")
    }

    override fun observeBytes(key: String): Flow<ByteArray> {
        TODO("Not yet implemented")
    }

    override fun observeDouble(key: String): Flow<Double> {
        TODO("Not yet implemented")
    }

    override fun observeFloat(key: String): Flow<Float> {
        TODO("Not yet implemented")
    }

    override fun observeInt(key: String): Flow<Int> {
        TODO("Not yet implemented")
    }

    override fun observeLong(key: String): Flow<Long> {
        TODO("Not yet implemented")
    }

    override fun <T> observeSerializable(key: String, deserializer: DeserializationStrategy<T>): Flow<T> {
        TODO("Not yet implemented")
    }

    override fun observeString(key: String): Flow<String> {
        TODO("Not yet implemented")
    }

    override fun putBoolean(key: String, value: Boolean): Outcome<Unit, Exception> {
        TODO("Not yet implemented")
    }

    override fun putBytes(key: String, value: ByteArray): Outcome<Unit, Exception> {
        TODO("Not yet implemented")
    }

    override fun putDouble(key: String, value: Double): Outcome<Unit, Exception> {
        TODO("Not yet implemented")
    }

    override fun putFloat(key: String, value: Float): Outcome<Unit, Exception> {
        TODO("Not yet implemented")
    }

    override fun putInt(key: String, value: Int): Outcome<Unit, Exception> {
        TODO("Not yet implemented")
    }

    override fun putLong(key: String, value: Long): Outcome<Unit, Exception> {
        TODO("Not yet implemented")
    }

    override fun <T> putSerializable(
        key: String,
        value: T,
        serializer: SerializationStrategy<T>
    ): Outcome<Unit, Exception> {
        TODO("Not yet implemented")
    }

    override fun putString(key: String, value: String): Outcome<Unit, Exception> {
        TODO("Not yet implemented")
    }

    override fun remove(key: String): Outcome<Unit, Exception> {
        TODO("Not yet implemented")
    }

    override fun transaction(block: (rollback: () -> Nothing) -> Unit) {
        try {
            val snapshot = storage.value
            val rollback = {
                storage.update { snapshot }
                throw KMPStorageRollbackException()
            }
            block(rollback)
        } catch (e: KMPStorageRollbackException) {
            // Do nothing
        }
    }

    override fun vacuum(): Outcome<Unit, Exception> {
        return Outcome.Ok(Unit)
    }

    // TODO Add persisting of in-memory nodes
//    suspend fun persist(): Outcome<Unit, Any> {
//
//    }
}

private class KMPStorageRollbackException : Exception("Transaction Rolled Back")