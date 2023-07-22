package com.ethossoftworks.land.common.service.preferences

import com.ethossoftworks.land.common.model.Contact
import com.outsidesource.oskitkmp.outcome.Outcome

interface IPreferencesService {
    suspend fun setSaveFolder(folder: String): Outcome<Unit, Any>
    suspend fun getSaveFolder(): Outcome<String, Any>
    suspend fun setDisplayName(name: String): Outcome<Unit, Any>
    suspend fun getDisplayName(): Outcome<String, Any>
    suspend fun setContacts(contacts: Map<String, Contact>): Outcome<Unit, Any>
    suspend fun getContacts(): Map<String, Contact>
}