package com.ethossoftworks.land.service.filetransfer

import co.touchlab.kermit.Logger
import com.ethossoftworks.land.entity.Device
import com.ethossoftworks.land.entity.DevicePlatform
import com.ethossoftworks.land.entity.toDevicePlatform
import com.ethossoftworks.land.lib.SequentialBufferPool
import com.ethossoftworks.land.lib.bytes.offsetContentEquals
import com.ethossoftworks.land.lib.bytes.toUShort
import com.outsidesource.oskitkmp.concurrency.asyncOutcome
import com.outsidesource.oskitkmp.concurrency.awaitOutcome
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.channelFlowWithDefer
import com.outsidesource.oskitkmp.lib.coroutineScopeWithDefer
import com.outsidesource.oskitkmp.lib.current
import com.outsidesource.oskitkmp.outcome.Outcome
import com.outsidesource.oskitkmp.outcome.runOnError
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
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
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import okio.BufferedSink
import okio.BufferedSource
import okio.Sink
import okio.buffer
import kotlin.experimental.xor

const val FILE_TRANSFER_PORT = 50077
private const val AUTH_CHALLENGE_LENGTH = 32
private const val PROTOCOL_VERSION = 1

@OptIn(ExperimentalUnsignedTypes::class)
private val cancellationSignalBytes = ubyteArrayOf(0x75u, 0xE6u, 0x07u, 0x9Eu, 0x8Du, 0x32u, 0x7Au).toByteArray()

private data class CancellationSignal(val transferId: Short, val command: CancellationCommand)

private class LANdTransferCancelledException(val command: CancellationCommand) : CancellationException("LANd Transfer Cancelled")

private class TransferReceiveContext(
    val transferId: Short,
    val readBuffer: ByteArray,
    val socketReadChannel: ByteReadChannel,
    val socketWriteChannel: ByteWriteChannel,
    val eventChannel: SendChannel<FileTransferServerEvent>,
)

private class TransferSendContext(
    val transferId: Short,
    val readBuffer: ByteArray,
    val socketReadChannel: ByteReadChannel,
    val socketWriteChannel: ByteWriteChannel,
)

class FileTransferService(
    private val getServerDeviceName: () -> String,
    private val getLocalIpAddress: suspend () -> String,
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

    /**
     * Receive File
     */
    private suspend fun receiveConnection(
        socket: Socket,
        eventChannel: SendChannel<FileTransferServerEvent>
    ) = coroutineScopeWithDefer { defer ->
        val transferId = generateConnectionId()
        var outerSocketWriteChannel: ByteWriteChannel? = null
        var outerFileWriter: BufferedSink? = null

        val receiveJob = asyncOutcome {
            val transferContext = TransferReceiveContext(
                transferId = transferId,
                readBuffer = ByteArray(bufferSize),
                socketReadChannel = socket.openReadChannel(),
                socketWriteChannel = socket.openWriteChannel(),
                eventChannel = eventChannel,
            ).apply {
                outerSocketWriteChannel = socketWriteChannel
            }

            defer {
                Logger.i { "File Transfer Service - Closing Receive Resources" }
                outerFileWriter?.runCatching { close() }
                transferContext.socketWriteChannel.close()
                socket.close()
            }

            receiverReadProtocolVersion(transferContext).unwrapOrReturn {
                return@asyncOutcome Outcome.Ok(Unit)
            }

            receiverHandleAuthChallenge(transferContext).unwrapOrReturn {
                return@asyncOutcome Outcome.Ok(Unit)
            }

            val header = receiverReadHeader(transferContext)

            if (header.command == FileTransferCommand.Connect) {
                receiverHandleConnectCommand(transferContext)
                return@asyncOutcome Outcome.Ok(Unit)
            }

            val response = receiverWaitForUserResponse(transferContext, header).unwrapOrReturn {
                return@asyncOutcome Outcome.Ok(Unit)
            }

            receiverReceiveFile(transferContext, header, response) { outerFileWriter = it }.unwrapOrReturn {
                return@asyncOutcome Outcome.Ok(Unit)
            }

            Outcome.Ok(Unit)
        }

        val cancellationListenerJob = launch {
            val signal = cancellationFlow.first { it.transferId == transferId }
            receiveJob.cancel(LANdTransferCancelledException(signal.command))
        }

        receiveJob.awaitOutcome().runOnError { error ->
            when {
                error is LANdTransferCancelledException -> {
                    outerSocketWriteChannel?.writeFully(
                        cancellationSignalBytes + error.command.toByte(),
                        0,
                        cancellationSignalBytes.size + 1
                    )
                    outerSocketWriteChannel?.flush()
                    val event = FileTransferServerEvent.TransferStopped(
                        transferId = transferId,
                        reason = FileTransferStopReason.UserCancelled(error.command, cancelledByLocalUser = true)
                    )
                    eventChannel.send(event)
                }

                isClosedConnectionException(error) -> {
                    val event = FileTransferServerEvent.TransferStopped(transferId, FileTransferStopReason.SocketClosed)
                    eventChannel.send(event)
                }

                else -> {
                    val event = FileTransferServerEvent.TransferStopped(transferId, FileTransferStopReason.Unknown)
                    eventChannel.send(event)
                }
            }
        }

        cancellationListenerJob.cancel()
    }

    private suspend fun receiverReadProtocolVersion(ctx: TransferReceiveContext): Outcome<Int, Unit> {
        val protocolVersion = ctx.socketReadChannel.readByte().toInt()
        if (protocolVersion > PROTOCOL_VERSION) {
            ctx.eventChannel.send(
                FileTransferServerEvent.TransferStopped(
                    transferId = ctx.transferId,
                    reason = FileTransferStopReason.UnknownProtocol,
                )
            )
            return Outcome.Error(Unit)
        }

        return Outcome.Ok(protocolVersion)
    }

    private suspend fun receiverHandleAuthChallenge(ctx: TransferReceiveContext): Outcome<Unit, Unit> {
        // Send authorization challenge
        val random = SecureRandom.nextBytes(AUTH_CHALLENGE_LENGTH)
        ctx.socketWriteChannel.writeFully(random, 0, random.size)
        ctx.socketWriteChannel.flush()

        // Receive authorization response
        ctx.socketReadChannel.readFully(ctx.readBuffer, 0, random.size)
        val authResponse = ctx.readBuffer.copyOfRange(0, random.size)
        if (!authResponse.contentEquals(calculateAuthResponse(random))) {
            ctx.eventChannel.send(
                FileTransferServerEvent.TransferStopped(
                    transferId = ctx.transferId,
                    reason = FileTransferStopReason.AuthorizationChallengeFail,
                )
            )
            return Outcome.Error(Unit)
        }

        return Outcome.Ok(Unit)
    }

    private suspend fun receiverReadHeader(ctx: TransferReceiveContext): FileTransferRequestHeader {
        // Read Fixed Header
        val command = ctx.socketReadChannel.readByte()
        val ipAddress = ByteArray(17).apply { ctx.socketReadChannel.readFully(this) }.toIPString()
        val port = ByteArray(2).apply { ctx.socketReadChannel.readFully(this) }.toUShort()
        val platform = ctx.socketReadChannel.readByte().toDevicePlatform()
        val senderNameLength = ctx.socketReadChannel.readByte().toInt()
        val fileNameLength = ctx.socketReadChannel.readShort().toInt()
        val payloadLength = ctx.socketReadChannel.readLong()
        ctx.socketReadChannel.readFully(ByteArray(24)) // Future use

        val senderName: String
        val fileName: String

        // Read Dynamic Header
        if (command == FileTransferCommand.Connect) {
            senderName = ""
            fileName = ""
        } else {
            senderName = ByteArray(senderNameLength).apply { ctx.socketReadChannel.readFully(this) }.decodeToString()
            fileName = ByteArray(fileNameLength).apply { ctx.socketReadChannel.readFully(this) }.decodeToString()
        }

        val header = FileTransferRequestHeader(
            ipAddress = ipAddress,
            command = command,
            payloadLength = payloadLength,
            platform = platform,
            fileName = fileName,
            senderName = senderName,
            port = port,
        )

        return header
    }

    private suspend fun receiverHandleConnectCommand(ctx: TransferReceiveContext) {
        val deviceName = getServerDeviceName()
        ctx.socketWriteChannel.writeByte(Platform.current.toDevicePlatform().toByte())
        ctx.socketWriteChannel.writeByte(deviceName.length)
        ctx.socketWriteChannel.writeStringUtf8(deviceName)
        ctx.socketWriteChannel.flush()
    }

    private suspend fun receiverWaitForUserResponse(
        ctx: TransferReceiveContext,
        header: FileTransferRequestHeader,
    ): Outcome<FileTransferResponse, Unit> {
        ctx.eventChannel.send(
            FileTransferServerEvent.TransferRequested(
                ctx.transferId,
                header.senderName,
                header.platform,
                header.ipAddress,
                header.port,
                header.fileName,
                header.payloadLength
            )
        )

        val response = transferResponseFlow.first { it.transferId == ctx.transferId }
        val responseByte = if (response.responseType == FileTransferResponseType.Accepted) 0x01 else 0x00

        ctx.socketWriteChannel.writeByte(responseByte)
        ctx.socketWriteChannel.writeLong(response.existingFileLength)
        ctx.socketWriteChannel.flush()

        if (response.responseType == FileTransferResponseType.Rejected) return Outcome.Error(Unit)
        return Outcome.Ok(response)
    }

    override suspend fun respondToTransferRequest(
        transferId: Short,
        existingFileLength: Long,
        response: FileTransferResponseType,
        sink: Sink?,
    ) {
        transferResponseFlow.emit(FileTransferResponse(transferId, response, existingFileLength, sink))
    }

    private suspend fun CoroutineScope.receiverReceiveFile(
        ctx: TransferReceiveContext,
        header: FileTransferRequestHeader,
        response: FileTransferResponse,
        setFileWriter: (BufferedSink) -> Unit,
    ): Outcome<Unit, Unit> {
        val sink = response.sink
        if (sink == null) {
            ctx.eventChannel.send(
                FileTransferServerEvent.TransferStopped(
                    ctx.transferId,
                    FileTransferStopReason.UnableToOpenFile
                )
            )
            return Outcome.Error(Unit)
        }

        val event = FileTransferServerEvent.TransferProgress(ctx.transferId, 0, header.payloadLength)
        ctx.eventChannel.send(event)

        val fileWriter = sink.buffer()
        setFileWriter(fileWriter)
        var totalWritten = response.existingFileLength

        val onDone = suspend {
            if (totalWritten == header.payloadLength) {
                ctx.eventChannel.send(FileTransferServerEvent.TransferComplete(ctx.transferId))
            } else {
                ctx.eventChannel.send(
                    FileTransferServerEvent.TransferStopped(
                        ctx.transferId,
                        FileTransferStopReason.SocketClosed
                    )
                )
            }
        }

        while (isActive) {
            if (totalWritten == header.payloadLength) {
                onDone()
                break
            }

            val read = ctx.socketReadChannel.readAvailable(ctx.readBuffer, 0, bufferSize)
            if (read == 0) continue

            if (read == -1) {
                onDone()
                break
            }

            // Check for cancellation signal in tail of read buffer
            if (ctx.readBuffer.offsetContentEquals(cancellationSignalBytes, read - cancellationSignalBytes.size - 1)) {
                val cancelCommand = CancellationCommand.fromByte(ctx.readBuffer[read - 1])
                ctx.eventChannel.send(
                    FileTransferServerEvent.TransferStopped(
                        ctx.transferId,
                        FileTransferStopReason.UserCancelled(cancelCommand)
                    )
                )
                break
            }

            fileWriter.write(ctx.readBuffer, 0, read)
            totalWritten += read

            val progressEvent = FileTransferServerEvent.TransferProgress(ctx.transferId, totalWritten, header.payloadLength)
            ctx.eventChannel.send(progressEvent)
        }

        return Outcome.Ok(Unit)
    }


    /**
     * Send File
     */
    override suspend fun sendFile(
        file: FileTransferRequest,
        destinationIp: String
    ): Flow<FileTransferClientEvent> = channelFlowWithDefer {defer ->
        val transferId = generateConnectionId()
        var outerSocketWriteChannel: ByteWriteChannel? = null
        var outerFileReader: BufferedSource? = null

        val sendJob = asyncOutcome sendJob@ {
            send(FileTransferClientEvent.Connecting(transferId))
            val socket = aSocket(selectorManager).tcp().connect(destinationIp, FILE_TRANSFER_PORT)

            val transferContext = TransferSendContext(
                transferId = transferId,
                readBuffer = ByteArray(bufferSize),
                socketReadChannel = socket.openReadChannel(),
                socketWriteChannel = socket.openWriteChannel(),
            ).apply {
                outerSocketWriteChannel = socketWriteChannel
            }

            defer {
                Logger.i { "File Transfer Service - Closing Send Resources" }
                outerFileReader?.runCatching { close() }
                transferContext.socketWriteChannel.close()
                socket.close()
            }

            senderSendProtocol(transferContext)

            senderHandleAuth(transferContext)

            senderSendHeader(transferContext, file)

            val existingFileLength = senderAwaitResponse(transferContext).unwrapOrReturn {
                return@sendJob Outcome.Ok(Unit)
            }

            val recipientCancellationListener = senderAwaitCancellation(transferContext) { this@sendJob.cancel() }

            senderSendFile(transferContext, file, existingFileLength) { outerFileReader = it }

            recipientCancellationListener.cancel()
            Outcome.Ok(Unit)
        }

        val cancellationListenerJob = launch {
            val signal = cancellationFlow.first { it.transferId == transferId }
            sendJob.cancel(LANdTransferCancelledException(signal.command))
        }

        sendJob.awaitOutcome().runOnError { error ->
            when {
                error is LANdTransferCancelledException -> {
                    outerSocketWriteChannel?.writeFully(cancellationSignalBytes + error.command.toByte(), 0, cancellationSignalBytes.size + 1)
                    outerSocketWriteChannel?.flush()
                    send(
                        FileTransferClientEvent.TransferStopped(
                            transferId = transferId,
                            reason = FileTransferStopReason.UserCancelled(error.command, cancelledByLocalUser = true)
                        )
                    )
                }
                isClosedConnectionException(error) -> {
                    send(FileTransferClientEvent.TransferStopped(transferId, FileTransferStopReason.SocketClosed))
                }
                else -> {
                    send(FileTransferClientEvent.TransferStopped(transferId, FileTransferStopReason.Unknown))
                }
            }
        }

        cancellationListenerJob.cancel()
    }.flowOn(Dispatchers.IO)

    private suspend fun senderSendProtocol(ctx: TransferSendContext) {
        ctx.socketWriteChannel.writeByte(PROTOCOL_VERSION)
        ctx.socketWriteChannel.flush()
    }

    private suspend fun senderHandleAuth(ctx: TransferSendContext) {
        ctx.socketReadChannel.readFully(ctx.readBuffer, 0, AUTH_CHALLENGE_LENGTH)
        val authChallengeResponse = calculateAuthResponse(ctx.readBuffer.copyOfRange(0, AUTH_CHALLENGE_LENGTH))
        ctx.socketWriteChannel.writeFully(authChallengeResponse, 0, AUTH_CHALLENGE_LENGTH)
        ctx.socketWriteChannel.flush()
    }

    private suspend fun senderSendHeader(ctx: TransferSendContext, file: FileTransferRequest) {
        // Send Fixed Header
        ctx.socketWriteChannel.writeByte(FileTransferCommand.FileTransfer) // Command
        ctx.socketWriteChannel.writeFully(getLocalIpAddress().toIPBytes())
        ctx.socketWriteChannel.writeShort(FILE_TRANSFER_PORT)
        ctx.socketWriteChannel.writeByte(Platform.current.toDevicePlatform().toByte())
        ctx.socketWriteChannel.writeByte(file.senderName.length) // Sender Name Length
        ctx.socketWriteChannel.writeShort(file.fileName.length.toShort()) // File Name Length
        ctx.socketWriteChannel.writeLong(file.length) // Payload Length
        ctx.socketWriteChannel.writeFully(ByteArray(24) { 0x00 }) // Future use
        ctx.socketWriteChannel.flush()

        // Send Dynamic Header
        ctx.socketWriteChannel.writeStringUtf8(file.senderName)
        ctx.socketWriteChannel.writeStringUtf8(file.fileName)
        ctx.socketWriteChannel.flush()
    }

    private suspend fun ProducerScope<FileTransferClientEvent>.senderAwaitResponse(
        ctx: TransferSendContext
    ): Outcome<Long, Unit> {
        send(FileTransferClientEvent.AwaitingAcceptance(ctx.transferId))
        val response = ctx.socketReadChannel.readByte().toInt()
        val existingFileLength = ctx.socketReadChannel.readLong()

        if (response == 0x01) {
            send(FileTransferClientEvent.TransferResponseReceived(ctx.transferId, FileTransferResponseType.Accepted))
        } else if (response == 0x00) {
            send(FileTransferClientEvent.TransferResponseReceived(ctx.transferId, FileTransferResponseType.Rejected))
            return Outcome.Error(Unit)
        }

        return Outcome.Ok(existingFileLength)
    }

    private fun ProducerScope<FileTransferClientEvent>.senderAwaitCancellation(
        ctx: TransferSendContext,
        onCancel: () -> Unit,
    ) = asyncOutcome {
        val cancellationSignalBuffer = ByteArray(cancellationSignalBytes.size + 1)
        while (isActive) {
            ctx.socketReadChannel.readAvailable(cancellationSignalBuffer)
            if (!cancellationSignalBuffer.offsetContentEquals(cancellationSignalBytes, 0)) continue

            val command = CancellationCommand.fromByte(cancellationSignalBuffer.last())
            send(FileTransferClientEvent.TransferStopped(ctx.transferId, FileTransferStopReason.UserCancelled(command)))
            onCancel()
        }

        Outcome.Ok(Unit)
    }

    private suspend fun ProducerScope<FileTransferClientEvent>.senderSendFile(
        ctx: TransferSendContext,
        file: FileTransferRequest,
        existingFileLength: Long,
        setFileReader: (BufferedSource) -> Unit
    ) {
        send(FileTransferClientEvent.TransferProgress(ctx.transferId, 0, file.length))
        val fileReader = file.source.buffer().apply { skip(existingFileLength) }
        setFileReader(fileReader)
        var totalRead = existingFileLength

        val bufferPool = SequentialBufferPool(10, bufferSize)

        coroutineScope {
            launch {
                while (isActive) {
                    val (bufferId, buffer) = bufferPool.getFreeBuffer()
                    val read = fileReader.read(buffer, 0, bufferSize)
                    bufferPool.markBufferFull(bufferId, read)
                    if (read == -1) break
                    totalRead += read
                }
            }

            launch {
                while (isActive) {
                    val (bufferId, read, buffer) = bufferPool.getFullBuffer()
                    if (read == -1) break
                    ctx.socketWriteChannel.writeFully(buffer, 0, read)
                    bufferPool.markBufferFree(bufferId)
                    send(FileTransferClientEvent.TransferProgress(ctx.transferId, totalRead, file.length))
                }
            }
        }

        ctx.socketWriteChannel.flush()
        if (totalRead == file.length) send(FileTransferClientEvent.TransferComplete(ctx.transferId))
    }

    /**
     * Test Connection
     */
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
                socketWriteChannel.writeByte(FileTransferCommand.Connect) // Command (future use)
                socketWriteChannel.writeFully(getLocalIpAddress().toIPBytes())
                socketWriteChannel.writeShort(FILE_TRANSFER_PORT)
                socketWriteChannel.writeByte(0) // Sender Name Length
                socketWriteChannel.writeShort(0) // File Name Length
                socketWriteChannel.writeLong(0) // Payload Length
                socketWriteChannel.writeFully(ByteArray(24) { 0x00 }) // Future use
                socketWriteChannel.flush()

                // Read response
                val platform = socketReadChannel.readByte().toDevicePlatform()
                val nameLength = socketReadChannel.readByte().toInt()
                val name = ByteArray(nameLength).apply { socketReadChannel.readFully(this) }.decodeToString()

                socketWriteChannel.close()
                socket.close()

                Outcome.Ok(Device(name = name, platform = platform, port = FILE_TRANSFER_PORT, ipAddress = destinationIp))
            }
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }
}

private fun calculateAuthResponse(random: ByteArray): ByteArray {
    val hashedBytes = random.sha256().bytes
    for (i in hashedBytes.indices) {
        hashedBytes[i] = hashedBytes[i] xor 0x55
    }
    return hashedBytes
}

expect fun FileTransferService.isClosedConnectionException(exception: Any): Boolean

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

private fun ByteArray.toIPString(): String {
    if (this[0] == 0x01.toByte()) return "" // IPV6 not supported yet
    if (this.size < 5) return "" // Invalid IPV4 (4 bytes + 1 version byte)

    return buildString {
        for (i in 1..4) {
            append(this@toIPString[i].toUByte().toString(10))
            if (i < 4) append(".")
        }
    }
}

// Only support IPV4 for now but leave room for IPV6 expansion
private fun String.toIPBytes(): ByteArray {
    return byteArrayOf(0x00) + split(".").map { it.toUInt().toByte() }.take(4).toByteArray() + ByteArray(12)
}