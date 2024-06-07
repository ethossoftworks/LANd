package com.ethossoftworks.land.service.openSourceLicenses

import co.touchlab.kermit.Logger
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import land.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

class OpenSourceLicensesService : IOpenSourceLicensesService {

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalResourceApi::class)
    override suspend fun fetchLicenses(): Outcome<List<OpenSourceDependency>, Exception> {
        try {
            val bytes = Res.readBytes("files/open_source_licenses.json")
            val licenses = json.decodeFromString<List<DependencyDTO>>(bytes.decodeToString()).map { dto ->
                OpenSourceDependency(
                    name = dto.project,
                    description = dto.description,
                    url = dto.url ?: "",
                    license = dto.licenses.firstOrNull()?.license ?: "Unknown",
                    licenseUrl = dto.licenses.firstOrNull()?.licenseUrl ?: "",
                    dependency = dto.project,
                    version = dto.version,
                )
            }
            return Outcome.Ok(licenses)
        } catch (e: Exception) {
            Logger.e("LicenseService", e)
            return Outcome.Error(e)
        }
    }
}

@Serializable
private data class DependencyDTO(
    val project: String,
    val description: String,
    val version: String,
    val url: String?,
    val licenses: List<LicenseDTO>
)

@Serializable
private data class LicenseDTO(
    val license: String,
    @SerialName("license_url")
    val licenseUrl: String,
)