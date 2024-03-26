package com.ethossoftworks.land.entity

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class Contact(
    val name: String,
    val ipAddress: String?,
)