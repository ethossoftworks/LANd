package com.ethossoftworks.land.common.service.discovery

import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okio.use
import java.net.DatagramSocket
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import javax.jmdns.ServiceTypeListener

class JVMNSDService : INSDService {
    private val jmDNS = JmDNS.create(getLocalIPAddress())

    override suspend fun registerService(
        type: String,
        name: String,
        port: Int,
        properties: Map<String, Any>
    ): Outcome<Unit, Exception> {
        return try {
            val info = ServiceInfo.create(type, name, port, 0, 0, properties)
            jmDNS.registerService(info)
            Outcome.Ok(Unit)
        } catch (e: Exception) {
            return Outcome.Error(e)
        }
    }

    override suspend fun unregisterService(type: String, name: String, port: Int) {
        val info = ServiceInfo.create(type, name, port, "")
        jmDNS.unregisterService(info)
    }

    override suspend fun discoverServiceTypes(): Flow<NSDServiceType> = callbackFlow {
        val listener = object : ServiceTypeListener {
            override fun serviceTypeAdded(event: ServiceEvent?) {
                if (event == null) return
                trySend(NSDServiceType(type = event.type, name = event.name))
            }

            override fun subTypeForServiceTypeAdded(event: ServiceEvent?) {
                if (event == null) return
                trySend(NSDServiceType(type = event.type, name = event.name))
            }
        }

        withContext(Dispatchers.IO) {
            jmDNS.addServiceTypeListener(listener)
        }

        awaitClose { jmDNS.removeServiceTypeListener(listener) }
    }.buffer(BUFFERED, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override suspend fun discoverServices(type: String): Flow<NSDService> = callbackFlow {
        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent?) {
                if (event == null) return
                println(event)
            }

            override fun serviceRemoved(event: ServiceEvent?) {
                if (event == null) return
                println(event)
            }

            override fun serviceResolved(event: ServiceEvent?) {
                if (event == null) return
                trySend(event.info.toNSDService())
            }

        }

        withContext(Dispatchers.IO) {
            jmDNS.addServiceListener(type, listener)
        }

        awaitClose { jmDNS.removeServiceListener(type, listener) }
    }.buffer(BUFFERED, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private fun getLocalIPAddress(): InetAddress {
        return try {
            DatagramSocket().use { socket ->
                socket.connect(InetAddress.getByName("8.8.8.8"), 10002)
                socket.localAddress
            }
        } catch (e: Exception) {
            InetAddress.getLocalHost()
        }
    }

    private fun ServiceInfo.toNSDService() = NSDService(
        type = type,
        name = name,
        application = application,
        protocol = protocol,
        domain = domain,
        subType = subtype,
        port = port,
        iPv4Addresses = inet4Addresses.mapNotNull { it.hostAddress }.toSet(),
        iPv6Addresses = inet6Addresses.mapNotNull { it.hostAddress }.toSet(),
        props = buildMap {
            propertyNames.iterator().forEach {
                put(it, getPropertyBytes(it))
            }
        },
    )
}