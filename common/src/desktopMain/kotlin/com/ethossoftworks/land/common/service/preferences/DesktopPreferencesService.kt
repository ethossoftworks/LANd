package com.ethossoftworks.land.common.service.preferences

import com.outsidesource.oskitkmp.outcome.Outcome
import java.util.prefs.Preferences

private const val SAVE_FOLDER_KEY = "saveFolder"
private const val DISPLAY_NAME_KEY = "displayName"

class DesktopPreferencesService: IPreferencesService {
    private val preferences = Preferences.userRoot().node("com.ethossoftworks.LANd.preferences")

    override suspend fun setSaveFolder(folder: String): Outcome<Unit, Any> {
        return try {
            preferences.put(SAVE_FOLDER_KEY, folder)
            preferences.flush()
            Outcome.Ok(Unit)
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun getSaveFolder(): Outcome<String, Any> {
        return try {
            val value = preferences.get(SAVE_FOLDER_KEY, null) ?: return Outcome.Error(Unit)
            Outcome.Ok(value)
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun setDisplayName(name: String): Outcome<Unit, Any> {
        return try {
            preferences.put(DISPLAY_NAME_KEY, name)
            preferences.flush()
            Outcome.Ok(Unit)
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun getDisplayName(): Outcome<String, Any> {
        return try {
            val value = preferences.get(DISPLAY_NAME_KEY, null) ?: return Outcome.Error(Unit)
            Outcome.Ok(value)
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }
}