package com.ethossoftworks.land.common.interactor.preferences

import com.ethossoftworks.land.common.service.file.IFileHandler
import com.ethossoftworks.land.common.service.preferences.IPreferencesService
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current
import com.outsidesource.oskitkmp.outcome.Outcome
import com.outsidesource.oskitkmp.outcome.getOrElse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class AppPreferencesState(
    val displayName: String = "",
    val saveFolder: String? = null,
)

class AppPreferencesInteractor(
    private val preferencesService: IPreferencesService,
    private val fileHandler: IFileHandler,
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
                        Platform.iOS -> "iOS"
                        Platform.Unknown -> "Unknown"
                    }
                    val defaultName = "$platform ${(Math.random() * 10_000).roundToInt()}"
                    setDisplayName(defaultName)
                    update { state -> state.copy(displayName = defaultName) }
                }
            }

            val saveFolder = preferencesService.getSaveFolder().getOrElse(fileHandler.defaultSaveFolder())
            update { state -> state.copy(saveFolder = saveFolder) }
            hasInitialized.complete(Unit)
        }
    }

    suspend fun awaitInitialization() {
        hasInitialized.join()
    }

    suspend fun setSaveFolder(folder: String): Outcome<Unit, Any> {
        val outcome = preferencesService.setSaveFolder(folder)
        if (outcome is Outcome.Ok) update { state -> state.copy(saveFolder = folder) }
        return outcome
    }

    suspend fun setDisplayName(name: String): Outcome<Unit, Any> {
        val outcome = preferencesService.setDisplayName(name)
        if (outcome is Outcome.Ok) update { state -> state.copy(displayName = name) }
        return outcome
    }
}