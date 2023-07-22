package com.ethossoftworks.land.common.service.preferences

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.ethossoftworks.land.common.model.Contact
import com.outsidesource.oskitkmp.outcome.Outcome

private const val SAVE_FOLDER_KEY = "saveFolder"
private const val DISPLAY_NAME_KEY = "displayName"

class AndroidPreferencesService(
    context: Context,
): IPreferencesService {

    private val preferences = context.getSharedPreferences("com.ethossoftworks.LANd.preferences", MODE_PRIVATE)

    override suspend fun setSaveFolder(folder: String): Outcome<Unit, Any> {
        return try {
            val editor = preferences.edit()
            editor.putString(SAVE_FOLDER_KEY, folder)
            editor.apply()
            Outcome.Ok(Unit)
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun getSaveFolder(): Outcome<String, Any> {
        return try {
            val value = preferences.getString(SAVE_FOLDER_KEY, null) ?: return Outcome.Error(Unit)
            Outcome.Ok(value)
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun setDisplayName(name: String): Outcome<Unit, Any> {
        return try {
            val editor = preferences.edit()
            editor.putString(DISPLAY_NAME_KEY, name)
            editor.apply()
            Outcome.Ok(Unit)
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun getDisplayName(): Outcome<String, Any> {
        return try {
            val value = preferences.getString(DISPLAY_NAME_KEY, null) ?: return Outcome.Error(Unit)
            Outcome.Ok(value)
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun setContacts(contacts: Map<String, Contact>): Outcome<Unit, Any> {
        TODO("Not yet implemented")
    }

    override suspend fun getContacts(): Map<String, Contact> {
        TODO("Not yet implemented")
    }
}