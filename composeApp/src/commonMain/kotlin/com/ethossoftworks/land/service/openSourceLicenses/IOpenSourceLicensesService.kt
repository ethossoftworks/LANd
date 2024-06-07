package com.ethossoftworks.land.service.openSourceLicenses

import com.outsidesource.oskitkmp.outcome.Outcome

interface IOpenSourceLicensesService {
    suspend fun fetchLicenses(): Outcome<List<OpenSourceDependency>, Exception>
}

data class OpenSourceDependency(
    val name: String,
    val description: String,
    val url: String,
    val license: String,
    val licenseUrl: String,
    val dependency: String,
    val version: String,
)