package com.ethossoftworks.land.common.service.discovery

import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.flow.Flow

interface INSDService {
    suspend fun registerService(
        type: String,
        name: String,
        port: Int,
        properties: Map<String, Any> = emptyMap()
    ): Outcome<Unit, Exception>
    suspend fun unregisterService(type: String, name: String, port: Int)
    suspend fun discoverServiceTypes(): Flow<NSDServiceType>
    suspend fun discoverServices(type: String): Flow<NSDService>
}

data class NSDServiceType(
    val type: String,
    val name: String,
)

data class NSDService(
    val application: String,
    val protocol: String,
    val domain: String,
    val type: String, // Fully qualified type i.e. "_hue._tcp.local."
    val subType: String,
    val name: String,
    val port: Int,
    val iPv4Addresses: Set<String>,
    val iPv6Addresses: Set<String>,
    val props: Map<String, ByteArray>,
)