package com.ethossoftworks.land.common.model

import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val name: String,
    val ipAddress: String?,
)