package com.ethossoftworks.land.ui.openSourceLicenses

import com.ethossoftworks.land.coordinator.AppCoordinator
import com.ethossoftworks.land.interactor.openSourceLicenses.OpenSourceLicensesInteractor
import com.ethossoftworks.land.service.openSourceLicenses.OpenSourceDependency
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.coroutines.launch

data class OpenSourceLicensesViewState(
    val isLoading: Boolean = false,
    val dependencies: List<OpenSourceDependency> = emptyList(),
)

class OpenSourceLicensesViewInteractor(
    private val osLicensesInteractor: OpenSourceLicensesInteractor,
    private val appCoordinator: AppCoordinator,
) : Interactor<OpenSourceLicensesViewState>(
    initialState = OpenSourceLicensesViewState(),
) {

    override fun computed(state: OpenSourceLicensesViewState): OpenSourceLicensesViewState {
        return state.copy(
            dependencies = osLicensesInteractor.state.dependencies
        )
    }

    fun onViewMounted() {
        interactorScope.launch {
            update { state -> state.copy(isLoading = true) }
            osLicensesInteractor.fetchLicenses()
            update { state -> state.copy(isLoading = false) }
        }
    }

    fun onBackClicked() {
        appCoordinator.pop()
    }
}