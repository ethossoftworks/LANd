package com.ethossoftworks.land.common.service.filetransfer

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import korlibs.crypto.SecureRandom
import korlibs.crypto.sha256
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.updateAndGet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlin.experimental.xor

const val FILE_TRANSFER_PORT = 7788
const val AUTH_CHALLENGE_LENGTH = 32

class FileTransferService: IFileTransferService {
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val bufferSize = 65_536
    private val requestId = atomic<Short>(0)

    private fun getRequestId(): Short {
        if (requestId.value == Short.MAX_VALUE) return requestId.updateAndGet { 0 }
        return requestId.updateAndGet { (it + 1).toShort() }
    }

    override suspend fun startServer(): Flow<FileTransferServerEvent> = channelFlow {
        withContext(Dispatchers.IO) {
            try {
                val serverSocket = aSocket(selectorManager).tcp().bind("0.0.0.0", FILE_TRANSFER_PORT)
                send(FileTransferServerEvent.ServerStarted)

                supervisorScope {
                    while (isActive) {
                        val socket = serverSocket.accept()

                        launch {
                            receiveConnection(socket, channel)
                        }
                    }
                }
            } catch (e: Exception) {
                send(FileTransferServerEvent.ServerStopped(e))
            }
        }
    }

    override suspend fun sendFile(
        file: FileTransfer,
        destinationIp: String
    ): Flow<FileTransferProgress> = channelFlow {
        val requestId = getRequestId()
        try {
            val socket = aSocket(selectorManager).tcp().connect(destinationIp, FILE_TRANSFER_PORT)
            val readBuffer = ByteArray(bufferSize)
            val writeBuffer = ByteArray(bufferSize)
            val readChannel = socket.openReadChannel()
            val writeChannel = socket.openWriteChannel()

            // Handle auth challenge
            readChannel.readFully(readBuffer, 0, AUTH_CHALLENGE_LENGTH)
            val authChallengeResponse = calculateAuthResponse(readBuffer.copyOfRange(0, AUTH_CHALLENGE_LENGTH))
            writeChannel.writeFully(authChallengeResponse, 0, AUTH_CHALLENGE_LENGTH)
            writeChannel.flush()
        } catch (e: Exception) {
            send(FileTransferProgress.TransferStopped(requestId, FileTransferStopReason.Unknown))
        }
    }

    private suspend fun receiveConnection(
        socket: Socket,
        sendChannel: SendChannel<FileTransferServerEvent>
    ) {
        val requestId = getRequestId()

        try {
            val readBuffer = ByteArray(bufferSize)
            val writeBuffer = ByteArray(bufferSize)
            val readChannel = socket.openReadChannel()
            val writeChannel = socket.openWriteChannel()

            // Write authorization challenge
            val random = SecureRandom.nextBytes(AUTH_CHALLENGE_LENGTH)
            writeChannel.writeFully(random, 0, random.size)
            writeChannel.flush()

            // Receive authorization response
            readChannel.readFully(readBuffer, 0, random.size)
            val authResponse = readBuffer.copyOfRange(0, random.size)
            if (!authResponse.contentEquals(calculateAuthResponse(random))) {
                sendChannel.send(
                    FileTransferServerEvent.TransferRejected(
                        requestId = requestId,
                        reason = FileTransferRejectReason.AuthorizationChallengeFail
                    )
                )
                socket.close()
            }
        } catch (e: Exception) {
            sendChannel.send(FileTransferServerEvent.TransferStopped(requestId, FileTransferStopReason.Unknown))
        }
    }

    private fun calculateAuthResponse(random: ByteArray): ByteArray {
        val hashedBytes = random.sha256().bytes
        for (i in hashedBytes.indices) {
            hashedBytes[i] = hashedBytes[i] xor 0x55
        }
        return hashedBytes
    }
}