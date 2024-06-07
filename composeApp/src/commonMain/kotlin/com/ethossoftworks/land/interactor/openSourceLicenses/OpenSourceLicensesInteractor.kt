package com.ethossoftworks.land.interactor.openSourceLicenses

import com.ethossoftworks.land.service.openSourceLicenses.IOpenSourceLicensesService
import com.ethossoftworks.land.service.openSourceLicenses.OpenSourceDependency
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.Outcome
import com.outsidesource.oskitkmp.outcome.runOnOk

data class OpenSourceLicencesState(
    val dependencies: List<OpenSourceDependency> = emptyList()
)

class OpenSourceLicensesInteractor(
    private val openSourceLicensesService: IOpenSourceLicensesService,
): Interactor<OpenSourceLicencesState>(
    initialState = OpenSourceLicencesState(),
) {

    suspend fun fetchLicenses(): Outcome<List<OpenSourceDependency>, Exception> {
        return openSourceLicensesService.fetchLicenses().runOnOk { dependencies ->
            update { state -> state.copy(dependencies = dependencies) }
        }
    }
}