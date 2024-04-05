package com.ethossoftworks.land.service.discovery

import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class IOSNSDService : INSDService {
    override suspend fun init() {}

    override suspend fun registerService(
        type: String,
        name: String,
        port: Int,
        properties: Map<String, Any>
    ): Outcome<Unit, Any> {
        return Outcome.Ok(Unit)
    }

    override suspend fun unregisterService(type: String, name: String, port: Int): Outcome<Unit, Any> {
        return Outcome.Ok(Unit)
    }

    override suspend fun observeServiceTypes(): Flow<NSDServiceType> {
        return emptyFlow()
    }

    override suspend fun observeServices(type: String): Flow<NSDServiceEvent> {
        return emptyFlow()
    }

    override suspend fun getLocalIpAddress(): String {
        return ""
    }
}