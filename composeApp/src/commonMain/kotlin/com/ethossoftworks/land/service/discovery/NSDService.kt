package com.ethossoftworks.land.service.discovery

import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.flow.Flow

interface INSDService {
    suspend fun init()
    suspend fun registerService(
        type: String,
        name: String,
        port: Int,
        properties: Map<String, Any> = emptyMap()
    ): Outcome<Unit, Any>
    suspend fun unregisterService(type: String, name: String, port: Int): Outcome<Unit, Any>
    suspend fun observeServiceTypes(): Flow<NSDServiceType>
    suspend fun observeServices(type: String): Flow<NSDServiceEvent>
    suspend fun getLocalIpAddress(): String
}

sealed class NSDServiceEvent {
    data class ServiceAdded(val service: NSDServicePartial) : NSDServiceEvent()
    data class ServiceResolved(val service: NSDService) : NSDServiceEvent()
    data class ServiceRemoved(val service: NSDServicePartial) : NSDServiceEvent()
}

data class NSDServiceType(
    val type: String,
)

data class NSDService(
    val type: String, // Fully qualified type i.e. "_hue._tcp.local."
    val name: String,
    val port: Int,
    val iPv4Addresses: Set<String>,
    val iPv6Addresses: Set<String>,
    val props: Map<String, ByteArray>,
)

data class NSDServicePartial(
    val type: String,
    val name: String,
)