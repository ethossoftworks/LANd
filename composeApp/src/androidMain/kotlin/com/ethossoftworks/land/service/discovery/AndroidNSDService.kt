package com.ethossoftworks.land.service.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.use
import java.net.DatagramSocket
import java.net.InetAddress
import javax.jmdns.*

enum class AndroidNSDError {
    NotProperlyInitialized
}

class AndroidNSDService(
    applicationContext: Context
) : INSDService {
    private val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val multicastLock = wifiManager.createMulticastLock("LANd").apply { setReferenceCounted(true) }
    private var jmDNS: JmDNS? = null
    private val isInitialized = CompletableDeferred<Unit>()

    override suspend fun init() {
        jmDNS = JmDNS.create(getLocalInetAddress())
        isInitialized.complete(Unit)
    }

    override suspend fun registerService(
        type: String,
        name: String,
        port: Int,
        properties: Map<String, Any>
    ): Outcome<Unit, Exception> {
        isInitialized.join()

        return try {
            val info = ServiceInfo.create(type, name, port, 0, 0, properties)
            jmDNS?.registerService(info)
            Outcome.Ok(Unit)
        } catch (e: Exception) {
            return Outcome.Error(e)
        }
    }

    override suspend fun unregisterService(type: String, name: String, port: Int): Outcome<Unit, Any> {
        isInitialized.join()
        val jmDNS = jmDNS ?: return Outcome.Error(AndroidNSDError.NotProperlyInitialized)
        return try {
            val info = ServiceInfo.create(type, name, port, "")
            jmDNS.unregisterService(info)
            Outcome.Ok(Unit)
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun observeServiceTypes(): Flow<NSDServiceType> = callbackFlow {
        isInitialized.join()
        multicastLock.acquire()

        val listener = object : ServiceTypeListener {
            override fun serviceTypeAdded(event: ServiceEvent?) {
                if (event == null) return
                launch { send(NSDServiceType(type = event.type)) }
            }

            override fun subTypeForServiceTypeAdded(event: ServiceEvent?) {
                if (event == null) return
                launch { send(NSDServiceType(type = event.type)) }
            }
        }

        withContext(Dispatchers.IO) {
            jmDNS?.addServiceTypeListener(listener)
        }

        awaitClose {
            jmDNS?.removeServiceTypeListener(listener)
            multicastLock.release()
        }
    }

    override suspend fun observeServices(type: String): Flow<NSDServiceEvent> = callbackFlow {
        isInitialized.join()
        multicastLock.acquire()

        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent?) {
                if (event == null) return
                launch { send(NSDServiceEvent.ServiceAdded(event.info.toNSDServicePartial())) }
            }

            override fun serviceRemoved(event: ServiceEvent?) {
                if (event == null) return
                launch { send(NSDServiceEvent.ServiceRemoved(event.info.toNSDServicePartial())) }
            }

            override fun serviceResolved(event: ServiceEvent?) {
                if (event == null) return
                launch { send(NSDServiceEvent.ServiceResolved(event.info.toNSDService())) }
            }
        }

        withContext(Dispatchers.IO) {
            jmDNS?.addServiceListener(type, listener)
        }

        awaitClose {
            jmDNS?.removeServiceListener(type, listener)
            multicastLock.release()
        }
    }

    override suspend fun getLocalIpAddress(): String? {
        return getLocalInetAddress()?.hostAddress
    }

    private suspend fun getLocalInetAddress(): InetAddress? {
        return try {
            val inetAddress = DatagramSocket().use { socket ->
                socket.connect(InetAddress.getByName("8.8.8.8"), 10002)
                socket.localAddress
            }
            if (inetAddress == InetAddress.getLocalHost() || inetAddress.hostAddress == "::") return null

            inetAddress
        } catch (e: Exception) {
            null
        }
    }

    private fun ServiceInfo.toNSDService() = NSDService(
        type = type,
        name = name,
        port = port,
        iPv4Addresses = inet4Addresses.mapNotNull { it.hostAddress }.toSet(),
        iPv6Addresses = inet6Addresses.mapNotNull { it.hostAddress }.toSet(),
        props = buildMap {
            propertyNames.iterator().forEach {
                put(it, getPropertyBytes(it))
            }
        },
    )

    private fun ServiceInfo.toNSDServicePartial() = NSDServicePartial(
        type = type,
        name = name,
    )
}