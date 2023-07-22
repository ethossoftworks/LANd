package com.ethossoftworks.land.common.ui.home

import com.ethossoftworks.land.common.interactor.preferences.AppPreferencesInteractor
import com.ethossoftworks.land.common.model.Contact
import com.ethossoftworks.land.common.service.file.IFileHandler
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.launch

data class SettingsBottomSheetState(
    val saveFolder: String? = null,
    val saveFolderExists: Boolean = false,
    val displayName: String = "",
    val contacts: Map<String, Contact> = emptyMap(),
)

class SettingsBottomSheetViewInteractor(
    private val preferencesInteractor: AppPreferencesInteractor,
    private val fileHandler: IFileHandler,
) : Interactor<SettingsBottomSheetState>(
    initialState = SettingsBottomSheetState(),
    dependencies = listOf(preferencesInteractor),
) {
    override fun computed(state: SettingsBottomSheetState): SettingsBottomSheetState {
        return state.copy(
            saveFolder = preferencesInteractor.state.saveFolder,
            displayName = preferencesInteractor.state.displayName,
        )
    }

    fun onSaveFolderChangeClicked() {
        interactorScope.launch {
            val folderOutcome = fileHandler.selectFolder()
            if (folderOutcome !is Outcome.Ok) return@launch

            val folder = folderOutcome.value ?: return@launch
            preferencesInteractor.setSaveFolder(folder)
        }
    }
}