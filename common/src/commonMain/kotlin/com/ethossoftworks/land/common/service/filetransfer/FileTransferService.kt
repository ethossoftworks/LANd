package com.ethossoftworks.land.common.service.filetransfer

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import korlibs.crypto.SecureRandom
import korlibs.crypto.sha256
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.updateAndGet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import okio.Sink
import okio.buffer
import kotlin.experimental.xor

const val FILE_TRANSFER_PORT = 7788
private const val AUTH_CHALLENGE_LENGTH = 32
private const val PROTOCOL_VERSION = 1

class FileTransferService(): IFileTransferService {
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val bufferSize = 65_536
    private val connectionId = atomic<Short>(0)
    private val transferResponseFlow = MutableSharedFlow<FileTransferResponse>()

    private fun generateConnectionId(): Short {
        if (connectionId.value == Short.MAX_VALUE) return connectionId.updateAndGet { 0 }
        return connectionId.updateAndGet { (it + 1).toShort() }
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

    override suspend fun respondToTransferRequest(
        requestId: Short,
        existingFileLength: Long,
        response: FileTransferResponseType,
        sink: Sink?,
    ) {
        transferResponseFlow.emit(FileTransferResponse(requestId, response, existingFileLength, sink))
    }

    override suspend fun sendFile(
        file: FileTransfer,
        destinationIp: String
    ): Flow<FileTransferClientEvent> = channelFlow {
        val requestId = generateConnectionId()
        try {
            val socket = aSocket(selectorManager).tcp().connect(destinationIp, FILE_TRANSFER_PORT)
            val readBuffer = ByteArray(bufferSize)
            val socketReadChannel = socket.openReadChannel()
            val socketWriteChannel = socket.openWriteChannel()

            // Handle auth challenge
            socketReadChannel.readFully(readBuffer, 0, AUTH_CHALLENGE_LENGTH)
            val authChallengeResponse = calculateAuthResponse(readBuffer.copyOfRange(0, AUTH_CHALLENGE_LENGTH))
            socketWriteChannel.writeFully(authChallengeResponse, 0, AUTH_CHALLENGE_LENGTH)
            socketWriteChannel.flush()

            // Send Fixed Header
            socketWriteChannel.writeByte(PROTOCOL_VERSION) // Header Version
            socketWriteChannel.writeByte(file.senderName.length) // Sender Name Length
            socketWriteChannel.writeShort(file.name.length.toShort()) // File Name Length
            socketWriteChannel.writeLong(file.length) // Payload Length
            socketWriteChannel.writeByte(0) // Is Folder
            socketWriteChannel.writeByte(0) // Command Channel (future use)
            socketWriteChannel.flush()

            // Send Dynamic Header
            socketWriteChannel.writeStringUtf8(file.senderName)
            socketWriteChannel.writeStringUtf8(file.name)
            socketWriteChannel.flush()

            // Await Response
            send(FileTransferClientEvent.AwaitingAcceptance(requestId))
            val response = socketReadChannel.readByte().toInt()
            val existingFileLength = socketReadChannel.readLong()

            if (response == 0x00) {
                send(FileTransferClientEvent.TransferStopped(requestId, FileTransferStopReason.Rejected))
                socket.close()
                return@channelFlow
            }

            // Send File
            send(FileTransferClientEvent.TransferProgress(requestId, 0, file.length))
            val reader = file.source.buffer()
            reader.skip(existingFileLength)
            var totalRead = existingFileLength

            while (currentCoroutineContext().isActive) {
                val read = reader.read(readBuffer, 0, bufferSize)
                if (read == -1) break
                socketWriteChannel.writeFully(readBuffer, 0, read)
                totalRead += read
                send(FileTransferClientEvent.TransferProgress(requestId, totalRead, file.length))
            }

            socketWriteChannel.flush()
            socket.close()
            send(FileTransferClientEvent.TransferComplete(requestId))
        } catch (e: Exception) {
            send(FileTransferClientEvent.TransferStopped(requestId, FileTransferStopReason.Unknown))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun receiveConnection(
        socket: Socket,
        eventChannel: SendChannel<FileTransferServerEvent>
    ) {
        val requestId = generateConnectionId()

        try {
            val readBuffer = ByteArray(bufferSize)
            val socketReadChannel = socket.openReadChannel()
            val socketWriteChannel = socket.openWriteChannel()

            // Write authorization challenge
            val random = SecureRandom.nextBytes(AUTH_CHALLENGE_LENGTH)
            socketWriteChannel.writeFully(random, 0, random.size)
            socketWriteChannel.flush()

            // Receive authorization response
            socketReadChannel.readFully(readBuffer, 0, random.size)
            val authResponse = readBuffer.copyOfRange(0, random.size)
            if (!authResponse.contentEquals(calculateAuthResponse(random))) {
                eventChannel.send(
                    FileTransferServerEvent.TransferStopped(
                        requestId = requestId,
                        reason = FileTransferStopReason.AuthorizationChallengeFail,
                    )
                )
                socket.close()
                return
            }

            // Read Fixed Header
            val headerVersion = socketReadChannel.readByte().toInt()
            val senderNameLength = socketReadChannel.readByte().toInt()
            val fileNameLength = socketReadChannel.readShort().toInt()
            val payloadLength = socketReadChannel.readLong()
            val isFolder = socketReadChannel.readByte().toInt()
            val command = socketReadChannel.readByte().toInt()

            // Read Dynamic Header
            val senderName = ByteArray(senderNameLength).apply { socketReadChannel.readFully(this) }.decodeToString()
            val fileName = ByteArray(fileNameLength).apply { socketReadChannel.readFully(this) }.decodeToString()

            // Wait for user response and send Response to client
            eventChannel.send(FileTransferServerEvent.TransferRequested(requestId, senderName, fileName, payloadLength))
            val response = transferResponseFlow.first { it.requestId == requestId }
            val responseByte = if (response.responseType == FileTransferResponseType.Accepted) 0x01 else 0x00
            socketWriteChannel.writeByte(responseByte)
            socketWriteChannel.writeLong(response.existingFileLength)
            socketWriteChannel.flush()

            if (response.responseType == FileTransferResponseType.Rejected) {
                socket.close()
                return
            }

            val sink = response.sink
            if (sink == null) {
                eventChannel.send(FileTransferServerEvent.TransferStopped(requestId, FileTransferStopReason.UnableToOpenFile))
                socket.close()
                return
            }

            eventChannel.send(FileTransferServerEvent.TransferProgress(requestId, 0, payloadLength))
            val writer = sink.buffer()
            var totalWritten = response.existingFileLength

            while (currentCoroutineContext().isActive) {
                val read = socketReadChannel.readAvailable(readBuffer, 0, bufferSize)
                if (read == 0) continue

                if (read == -1) {
                    writer.close()
                    socket.close()
                    if (totalWritten == payloadLength) {
                        eventChannel.send(FileTransferServerEvent.TransferComplete(requestId))
                    } else {
                        eventChannel.send(FileTransferServerEvent.TransferStopped(requestId, FileTransferStopReason.Unknown))
                    }
                    break
                }

                writer.write(readBuffer, 0, read)
                totalWritten += read
                eventChannel.send(FileTransferServerEvent.TransferProgress(requestId, totalWritten, payloadLength))
            }
        } catch (e: Exception) {
            socket.close()
            eventChannel.send(FileTransferServerEvent.TransferStopped(requestId, FileTransferStopReason.Unknown))
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