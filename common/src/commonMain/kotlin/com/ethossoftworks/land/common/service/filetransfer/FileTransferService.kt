package com.ethossoftworks.land.common.service.filetransfer

import com.ethossoftworks.land.common.lib.coroutines.asyncOutcome
import com.ethossoftworks.land.common.lib.coroutines.awaitOutcome
import com.ethossoftworks.land.common.model.device.Device
import com.ethossoftworks.land.common.model.device.DevicePlatform
import com.ethossoftworks.land.common.model.device.toDevicePlatform
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current
import com.outsidesource.oskitkmp.outcome.Outcome
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import korlibs.crypto.SecureRandom
import korlibs.crypto.sha256
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.atomicfu.updateAndGet
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import okio.Sink
import okio.buffer
import kotlin.experimental.xor

const val FILE_TRANSFER_PORT = 7788
private const val AUTH_CHALLENGE_LENGTH = 32
private const val PROTOCOL_VERSION = 1

private object ClientCommand {
    const val Connect = 0x00.toByte()
    const val FileTransfer = 0x01.toByte()
}

@OptIn(ExperimentalUnsignedTypes::class)
private val cancellationSignalBytes = ubyteArrayOf(0x75u, 0xE6u, 0x07u, 0x9Eu, 0x8Du, 0x32u, 0x7Au).toByteArray()

private data class CancellationSignal(val transferId: Short, val command: CancellationCommand)

private class LANdTransferCancelledException(val command: CancellationCommand) : CancellationException()

class FileTransferService(
    private val getServerDeviceName: () -> String,
): IFileTransferService {
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val bufferSize = 65_536
    private val connectionId = atomic<Short>(0)
    private val transferResponseFlow = MutableSharedFlow<FileTransferResponse>()
    private val serverSocket = atomic<ServerSocket?>(null)
    private val cancellationFlow = MutableSharedFlow<CancellationSignal>()

    private fun generateConnectionId(): Short {
        if (connectionId.value == Short.MAX_VALUE) return connectionId.updateAndGet { 0 }
        return connectionId.updateAndGet { (it + 1).toShort() }
    }

    override suspend fun cancelTransfer(transferId: Short, command: CancellationCommand) {
        cancellationFlow.emit(CancellationSignal(transferId, command))
    }

    override suspend fun startServer(): Flow<FileTransferServerEvent> {
        return channelFlow {
            try {
                serverSocket.update { aSocket(selectorManager).tcp().bind("0.0.0.0", FILE_TRANSFER_PORT) }
                send(FileTransferServerEvent.ServerStarted)

                supervisorScope {
                    while (isActive) {
                        val socket = serverSocket.value?.accept() ?: continue
                        launch { receiveConnection(socket, channel) }
                    }
                }
            } catch (e: Exception) {
                send(FileTransferServerEvent.ServerStopped(e))
            }
        }.onCompletion {
            try {
                serverSocket.value?.close()
            } catch (e: Exception) {
                emit(FileTransferServerEvent.ServerStopped(e))
            }
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun receiveConnection(
        socket: Socket,
        eventChannel: SendChannel<FileTransferServerEvent>
    ) = coroutineScope {
        val transferId = generateConnectionId()

        val receiveJob = asyncOutcome {
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
                eventChannel.send(FileTransferServerEvent.TransferStopped(transferId = transferId, reason = FileTransferStopReason.AuthorizationChallengeFail,))
                socket.close()
                return@asyncOutcome
            }

            // Read Fixed Header
            val protocolVersion = socketReadChannel.readByte().toInt()
            val command = socketReadChannel.readByte()
            val senderNameLength = socketReadChannel.readByte().toInt()
            val fileNameLength = socketReadChannel.readShort().toInt()
            val payloadLength = socketReadChannel.readLong()
            socketReadChannel.readFully(ByteArray(24)) // Future use

            // Handle connect command
            if (command == ClientCommand.Connect) {
                val deviceName = getServerDeviceName()
                socketWriteChannel.writeByte(Platform.current.toDevicePlatform().toByte())
                socketWriteChannel.writeByte(deviceName.length)
                socketWriteChannel.writeStringUtf8(deviceName)
                socketWriteChannel.flush()
                socket.close()
                return@asyncOutcome
            }

            // Read Dynamic Header
            val senderName =
                ByteArray(senderNameLength).apply { socketReadChannel.readFully(this) }.decodeToString()
            val fileName = ByteArray(fileNameLength).apply { socketReadChannel.readFully(this) }.decodeToString()

            // Wait for user response and send response to client
            eventChannel.send(
                FileTransferServerEvent.TransferRequested(transferId, senderName, fileName, payloadLength)
            )
            val response = transferResponseFlow.first { it.transferId == transferId }
            val responseByte = if (response.responseType == FileTransferResponseType.Accepted) 0x01 else 0x00
            socketWriteChannel.writeByte(responseByte)
            socketWriteChannel.writeLong(response.existingFileLength)
            socketWriteChannel.flush()

            if (response.responseType == FileTransferResponseType.Rejected) {
                socket.close()
                return@asyncOutcome
            }

            val sink = response.sink
            if (sink == null) {
                eventChannel.send(FileTransferServerEvent.TransferStopped(transferId, FileTransferStopReason.UnableToOpenFile))
                socket.close()
                return@asyncOutcome
            }

            eventChannel.send(FileTransferServerEvent.TransferProgress(transferId, 0, payloadLength))
            val writer = sink.buffer()
            var totalWritten = response.existingFileLength
            var lastRead = 0

            while (currentCoroutineContext().isActive) {
                val read = socketReadChannel.readAvailable(readBuffer, 0, bufferSize)
                if (read == 0) continue

                if (read == -1) {
                    writer.close()
                    socket.close()
                    if (totalWritten == payloadLength) {
                        eventChannel.send(FileTransferServerEvent.TransferComplete(transferId))
                    } else if (lastRead < cancellationSignalBytes.size + 1) {
                        eventChannel.send(FileTransferServerEvent.TransferStopped(transferId, FileTransferStopReason.SocketClosed))
                    } else {
                        val cancellationCheck = readBuffer.copyOfRange(lastRead - cancellationSignalBytes.size - 1, lastRead - 1)
                        if (cancellationCheck.contentEquals(cancellationSignalBytes)) {
                            val cancelCommand = CancellationCommand.fromByte(readBuffer[lastRead - 1])
                            eventChannel.send(FileTransferServerEvent.TransferStopped(transferId, FileTransferStopReason.Cancelled(cancelCommand)))
                        } else {
                            eventChannel.send(FileTransferServerEvent.TransferStopped(transferId, FileTransferStopReason.SocketClosed))
                        }
                    }
                    break
                }

                writer.write(readBuffer, 0, read)
                totalWritten += read
                eventChannel.send(FileTransferServerEvent.TransferProgress(transferId, totalWritten, payloadLength))
                lastRead = read
            }
        }

        val cancellationListenerJob = launch {
            val signal = cancellationFlow.first { it.transferId == transferId }
            receiveJob.cancel(LANdTransferCancelledException(signal.command))
        }

        val outcome = receiveJob.awaitOutcome()
        if (outcome is Outcome.Error) {
            val error = outcome.error
            when {
                error is LANdTransferCancelledException -> {
                    eventChannel.send(FileTransferServerEvent.TransferStopped(
                        transferId = transferId,
                        reason = FileTransferStopReason.Cancelled(error.command, cancelledByLocalUser = true)
                    ))
                }
                isClosedConnectionException(error) -> {
                    eventChannel.send(FileTransferServerEvent.TransferStopped(transferId, FileTransferStopReason.SocketClosed))
                }
                else -> {
                    eventChannel.send(FileTransferServerEvent.TransferStopped(transferId, FileTransferStopReason.Unknown))
                }
            }
        }

        socket.close()
        cancellationListenerJob.cancel()
    }

    override suspend fun respondToTransferRequest(
        transferId: Short,
        existingFileLength: Long,
        response: FileTransferResponseType,
        sink: Sink?,
    ) {
        transferResponseFlow.emit(FileTransferResponse(transferId, response, existingFileLength, sink))
    }

    override suspend fun sendFile(
        file: FileTransferRequest,
        destinationIp: String
    ): Flow<FileTransferClientEvent> = channelFlow {
        val transferId = generateConnectionId()
        val readBuffer = ByteArray(bufferSize)
        var outerSocket: ASocket? = null
        var outerSocketWriteChannel: ByteWriteChannel? = null

        val sendJob = asyncOutcome {
            send(FileTransferClientEvent.Connecting(transferId))
            val socket = aSocket(selectorManager).tcp().connect(destinationIp, FILE_TRANSFER_PORT)
            val socketReadChannel = socket.openReadChannel()
            val socketWriteChannel = socket.openWriteChannel()
            outerSocket = socket
            outerSocketWriteChannel = socketWriteChannel

            // Handle auth challenge
            socketReadChannel.readFully(readBuffer, 0, AUTH_CHALLENGE_LENGTH)
            val authChallengeResponse = calculateAuthResponse(readBuffer.copyOfRange(0, AUTH_CHALLENGE_LENGTH))
            socketWriteChannel.writeFully(authChallengeResponse, 0, AUTH_CHALLENGE_LENGTH)
            socketWriteChannel.flush()

            // Send Fixed Header
            socketWriteChannel.writeByte(PROTOCOL_VERSION) // Header Version
            socketWriteChannel.writeByte(ClientCommand.FileTransfer) // Command
            socketWriteChannel.writeByte(file.senderName.length) // Sender Name Length
            socketWriteChannel.writeShort(file.fileName.length.toShort()) // File Name Length
            socketWriteChannel.writeLong(file.length) // Payload Length
            socketWriteChannel.writeFully(ByteArray(24) { 0x00 }) // Future use
            socketWriteChannel.flush()

            // Send Dynamic Header
            socketWriteChannel.writeStringUtf8(file.senderName)
            socketWriteChannel.writeStringUtf8(file.fileName)
            socketWriteChannel.flush()

            // Await Response
            send(FileTransferClientEvent.AwaitingAcceptance(transferId))
            val response = socketReadChannel.readByte().toInt()
            val existingFileLength = socketReadChannel.readLong()

            if (response == 0x01) {
                send(FileTransferClientEvent.TransferResponseReceived(transferId, FileTransferResponseType.Accepted))
            } else if (response == 0x00) {
                send(FileTransferClientEvent.TransferResponseReceived(transferId, FileTransferResponseType.Rejected))
                socket.close()
                return@asyncOutcome
            }

            // Send File
            send(FileTransferClientEvent.TransferProgress(transferId, 0, file.length))
            val reader = file.source.buffer()
            reader.skip(existingFileLength)
            var totalRead = existingFileLength

            while (currentCoroutineContext().isActive) {
                val read = reader.read(readBuffer, 0, bufferSize)
                if (read == -1) break
                socketWriteChannel.writeFully(readBuffer, 0, read)
                totalRead += read
                send(FileTransferClientEvent.TransferProgress(transferId, totalRead, file.length))
            }

            socketWriteChannel.flush()
            if (totalRead == file.length) send(FileTransferClientEvent.TransferComplete(transferId))
        }

        val cancellationListenerJob = launch {
            val signal = cancellationFlow.first { it.transferId == transferId }
            sendJob.cancel(LANdTransferCancelledException(signal.command))
        }

        val outcome = sendJob.awaitOutcome()
        if (outcome is Outcome.Error) {
            val error = outcome.error
            when {
                error is LANdTransferCancelledException -> {
                    outerSocketWriteChannel?.writeFully(cancellationSignalBytes + error.command.toByte(), 0, cancellationSignalBytes.size + 1)
                    outerSocketWriteChannel?.flush()
                    send(FileTransferClientEvent.TransferStopped(
                        transferId = transferId,
                        reason = FileTransferStopReason.Cancelled(error.command, cancelledByLocalUser = true)
                    ))
                }
                isClosedConnectionException(outcome.error) -> {
                    send(FileTransferClientEvent.TransferStopped(transferId, FileTransferStopReason.SocketClosed))
                }
                else -> {
                    send(FileTransferClientEvent.TransferStopped(transferId, FileTransferStopReason.Unknown))
                }
            }
        }

        outerSocket?.close()
        cancellationListenerJob.cancel()
    }.flowOn(Dispatchers.IO)


    override suspend fun testConnection(destinationIp: String): Outcome<Device, Exception> {
        return try {
            withTimeout(1_000) {
                val socket = aSocket(selectorManager).tcp().connect(destinationIp, FILE_TRANSFER_PORT)
                val readBuffer = ByteArray(bufferSize)
                val socketReadChannel = socket.openReadChannel()
                val socketWriteChannel = socket.openWriteChannel()

                // Handle auth challenge
                socketReadChannel.readFully(readBuffer, 0, AUTH_CHALLENGE_LENGTH)
                val authChallengeResponse = calculateAuthResponse(readBuffer.copyOfRange(0, AUTH_CHALLENGE_LENGTH))
                socketWriteChannel.writeFully(authChallengeResponse, 0, AUTH_CHALLENGE_LENGTH)
                socketWriteChannel.flush()

                // Send fixed header
                socketWriteChannel.writeByte(PROTOCOL_VERSION) // Header Version
                socketWriteChannel.writeByte(ClientCommand.Connect) // Command (future use)
                socketWriteChannel.writeByte(0) // Sender Name Length
                socketWriteChannel.writeShort(0) // File Name Length
                socketWriteChannel.writeLong(0) // Payload Length
                socketWriteChannel.writeFully(ByteArray(24) { 0x00 }) // Future use
                socketWriteChannel.flush()

                // Read response
                val platform = socketReadChannel.readByte().toDevicePlatform()
                val nameLength = socketReadChannel.readByte().toInt()
                val name = ByteArray(nameLength).apply { socketReadChannel.readFully(this) }.decodeToString()

                Outcome.Ok(Device(name = name, platform = platform, ipAddress = destinationIp))
            }
        } catch (e: Exception) {
            Outcome.Error(e)
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

expect fun FileTransferService.isClosedConnectionException(exception: Throwable): Boolean

private fun DevicePlatform.toByte() = when(this) {
    DevicePlatform.MacOS -> 0x00
    DevicePlatform.Windows -> 0x01
    DevicePlatform.Linux -> 0x02
    DevicePlatform.iOS -> 0x03
    DevicePlatform.Android -> 0x04
    DevicePlatform.Unknown -> 0xFF
}

private fun Byte.toDevicePlatform() = when(this) {
    0x00.toByte() -> DevicePlatform.MacOS
    0x01.toByte() -> DevicePlatform.Windows
    0x02.toByte() -> DevicePlatform.Linux
    0x03.toByte() -> DevicePlatform.iOS
    0x04.toByte() -> DevicePlatform.Android
    else -> DevicePlatform.Unknown
}

fun CancellationCommand.toByte() = when (this) {
    CancellationCommand.Stop -> 0x00.toByte()
    CancellationCommand.Delete -> 0x01.toByte()
}

fun CancellationCommand.Companion.fromByte(value: Byte) = when (value) {
    0x00.toByte() -> CancellationCommand.Stop
    0x01.toByte() -> CancellationCommand.Delete
    else -> CancellationCommand.Stop
}