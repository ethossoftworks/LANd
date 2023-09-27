package com.ethossoftworks.land.common.service.preferences

import com.ethossoftworks.land.common.model.Contact
import com.outsidesource.oskitkmp.file.KMPFileRef
import com.outsidesource.oskitkmp.outcome.Outcome

interface IPreferencesService {
    suspend fun setSaveFolder(folder: KMPFileRef): Outcome<Unit, Any>
    suspend fun getSaveFolder(): Outcome<KMPFileRef, Any>
    suspend fun setDisplayName(name: String): Outcome<Unit, Any>
    suspend fun getDisplayName(): Outcome<String, Any>
    suspend fun addContact(contact: Contact): Outcome<Unit, Any>
    suspend fun removeContact(contact: Contact): Outcome<Unit, Any>
    suspend fun getContacts(): Outcome<Map<String, Contact>, Any>
    suspend fun getVisibility(): Outcome<DeviceVisibility, Any>
    suspend fun setVisibility(visibility: DeviceVisibility): Outcome<Unit, Any>
    suspend fun getTransferRequestPermission(): Outcome<TransferRequestPermissionType, Any>
    suspend fun setTransferRequestPermission(type: TransferRequestPermissionType): Outcome<Unit, Any>
}

enum class DeviceVisibility {
    Visible,
    Hidden,
    SendOnly,
}

enum class TransferRequestPermissionType {
    AskAll,
    AcceptContacts,
    AcceptAll,
}

fun TransferRequestPermissionType.toId() = when(this) {
    TransferRequestPermissionType.AskAll -> 0
    TransferRequestPermissionType.AcceptContacts -> 1
    TransferRequestPermissionType.AcceptAll -> 2
}

fun Int.toTransferRequestPermissionType() = when(this) {
    0 -> TransferRequestPermissionType.AskAll
    1 -> TransferRequestPermissionType.AcceptContacts
    2 -> TransferRequestPermissionType.AcceptAll
    else -> TransferRequestPermissionType.AskAll
}

fun DeviceVisibility.toId() = when(this) {
    DeviceVisibility.Visible -> 0
    DeviceVisibility.Hidden -> 1
    DeviceVisibility.SendOnly -> 2
}

fun Int.toDeviceVisibility() = when(this) {
    0 -> DeviceVisibility.Visible
    1 -> DeviceVisibility.Hidden
    2 -> DeviceVisibility.SendOnly
    else -> DeviceVisibility.Visible
}