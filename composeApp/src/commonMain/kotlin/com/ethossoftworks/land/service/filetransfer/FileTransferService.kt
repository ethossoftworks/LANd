@file:OptIn(ExperimentalUnsignedTypes::class)

package com.ethossoftworks.land.service.filetransfer

import co.touchlab.kermit.Logger
import com.ethossoftworks.land.entity.Device
import com.ethossoftworks.land.entity.DevicePlatform
import com.ethossoftworks.land.entity.toDevicePlatform
import com.ethossoftworks.land.lib.bytes.find
import com.ethossoftworks.land.lib.bytes.offsetContentEquals
import com.ethossoftworks.land.lib.bytes.toUShort
import com.ethossoftworks.land.lib.crypto.DHKey
import com.ionspin.kotlin.bignum.integer.BigInteger
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
import korlibs.crypto.AES
import korlibs.crypto.CipherPadding
import korlibs.crypto.SecureRandom
import korlibs.crypto.sha256
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.atomicfu.updateAndGet
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import okio.*
import kotlin.experimental.xor

const val FILE_TRANSFER_PORT = 50077
private const val AUTH_CHALLENGE_LENGTH = 32
private const val PROTOCOL_VERSION = 1
private val FIXED_HEADER_SIZE = 56

// This is not meant to be cryptographically secure. This is just to hopefully prevent non-LANd clients from opening sockets
@OptIn(ExperimentalUnsignedTypes::class)
private val AUTH_CHALLENGE_XOR = ubyteArrayOf(
    0x65u, 0xB7u, 0x79u, 0x96u, 0x76u, 0x04u, 0x6Cu, 0xDEu,
    0xF2u, 0xD0u, 0x8Cu, 0x0Fu, 0x87u, 0x4Eu, 0x7Au, 0x26u,
    0x83u, 0xBAu, 0xF0u, 0x80u, 0xFCu, 0x08u, 0x58u, 0x20u,
    0xA5u, 0xFAu, 0x16u, 0x29u, 0x11u, 0xF5u, 0xACu, 0xBCu,
)

@OptIn(ExperimentalUnsignedTypes::class)
private val commandSignalBytes = ubyteArrayOf(0x75u, 0xE6u, 0x07u, 0x9Eu, 0x8Du, 0x32u, 0x7Au).toByteArray()

private data class CommandSignal(val transferId: Short, val command: Command)

private class LANdTransferCancelledException(val command: Command) : CancellationException("LANd Transfer Cancelled")

private interface IEncryptorContext {
    var useEncryption: Boolean
    var encryptionKey: ByteArray
    var encryptionIv: ByteArray
    val socketReadChannel: ByteReadChannel
    val socketWriteChannel: ByteWriteChannel
}

private class TransferReceiveContext(
    val transferId: Short,
    val readBuffer: ByteArray,
    val eventChannel: SendChannel<FileTransferServerEvent>,
    override val socketReadChannel: ByteReadChannel,
    override val socketWriteChannel: ByteWriteChannel,
    override var useEncryption: Boolean = false,
    override var encryptionKey: ByteArray = byteArrayOf(),
    override var encryptionIv: ByteArray = byteArrayOf(),
): IEncryptorContext

private class TransferSendContext(
    val transferId: Short,
    val readBuffer: ByteArray,
    override val socketReadChannel: ByteReadChannel,
    override val socketWriteChannel: ByteWriteChannel,
    override var useEncryption: Boolean = false,
    override var encryptionKey: ByteArray = byteArrayOf(),
    override var encryptionIv: ByteArray = byteArrayOf(),
): IEncryptorContext

private data class TransferFlags(
    val useEncryption: Boolean = false,
) {
    companion object {
        fun toBytes(flags: TransferFlags): Byte = if (flags.useEncryption) 0x01.toByte() else 0x00.toByte()

        fun fromBytes(byte: Byte): TransferFlags = TransferFlags(
            useEncryption = byte.toInt() and 0x01 > 0
        )
    }
}

class FileTransferService(
    private val getServerDeviceName: () -> String,
    private val getLocalIpAddress: suspend () -> String?,
    private val getUseEncryption: suspend () -> Boolean,
): IFileTransferService {
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val bufferSize = 65_536
    private val connectionId = atomic<Short>(0)
    private val transferResponseFlow = MutableSharedFlow<FileTransferResponse>()
    private val serverSocket = atomic<ServerSocket?>(null)
    private val commandFlow = MutableSharedFlow<CommandSignal>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val privateKey = CompletableDeferred<BigInteger>()
    private val publicKey = CompletableDeferred<BigInteger>()

    init {
        generateKeys()
    }

    private fun generateKeys() {
        scope.launch {
            val privKey = DHKey.generatePrivateKey()
            privateKey.complete(privKey)

            val pubKey = DHKey.computePublicKey(privKey)
            publicKey.complete(pubKey)
        }
    }

    private fun generateConnectionId(): Short {
        if (connectionId.value == Short.MAX_VALUE) return connectionId.updateAndGet { 0 }
        return connectionId.updateAndGet { (it + 1).toShort() }
    }

    override suspend fun sendCommandSignal(transferId: Short, command: Command) =
        commandFlow.emit(CommandSignal(transferId, command))

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

            val header = withTimeout(3_000) {
                receiverReadProtocolVersion(transferContext).unwrapOrReturn { return@withTimeout this }
                receiverHandleAuthChallenge(transferContext).unwrapOrReturn { return@withTimeout this }
                receiverHandleFlags(transferContext).unwrapOrReturn { return@withTimeout this }

                if (transferContext.useEncryption) {
                    receiverHandleEncryptionHandshake(transferContext).unwrapOrReturn { return@withTimeout this }
                }

                Outcome.Ok(receiverReadHeader(transferContext))
            }.unwrapOrReturn { return@asyncOutcome this }

            if (header.command == FileTransferCommand.Connect) {
                receiverHandleConnectCommand(transferContext)
                return@asyncOutcome Outcome.Ok(Unit)
            }

            val response = receiverWaitForUserResponse(transferContext, header).unwrapOrReturn {
                return@asyncOutcome this
            }

            receiverReceiveFile(transferContext, header, response) { outerFileWriter = it }.unwrapOrReturn {
                return@asyncOutcome this
            }

            Outcome.Ok(Unit)
        }

        val commandListenerJob = launch {
            val signal = commandFlow.first { it.transferId == transferId }
            when (signal.command) {
                Command.CancelStop,
                Command.CancelDelete -> receiveJob.cancel(LANdTransferCancelledException(signal.command))
                else -> {}
            }
        }

        receiveJob.awaitOutcome().runOnError { error ->
            when {
                error == Unit -> { /* Do Nothing */ }
                error is FileTransferStopReason -> {
                    eventChannel.send(FileTransferServerEvent.TransferStopped(transferId = transferId, reason = error))
                }
                error is LANdTransferCancelledException -> {
                    outerSocketWriteChannel?.writeFully(
                        commandSignalBytes + error.command.toByte(),
                        0,
                        commandSignalBytes.size + 1
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

        commandListenerJob.cancel()
    }

    private suspend fun receiverReadProtocolVersion(ctx: TransferReceiveContext): Outcome<Int, FileTransferStopReason> {
        val protocolVersion = ctx.socketReadChannel.readByte().toInt()
        if (protocolVersion > PROTOCOL_VERSION) return Outcome.Error(FileTransferStopReason.UnknownProtocol)
        return Outcome.Ok(protocolVersion)
    }

    private suspend fun receiverHandleAuthChallenge(ctx: TransferReceiveContext): Outcome<Unit, FileTransferStopReason> {
        // Send authorization challenge
        val random = SecureRandom.nextBytes(AUTH_CHALLENGE_LENGTH)
        ctx.socketWriteChannel.writeFully(random, 0, random.size)
        ctx.socketWriteChannel.flush()

        // Receive authorization response
        ctx.socketReadChannel.readFully(ctx.readBuffer, 0, random.size)
        val authResponse = ctx.readBuffer.copyOfRange(0, random.size)
        if (!authResponse.contentEquals(calculateAuthResponse(random))) {
            return Outcome.Error(FileTransferStopReason.AuthorizationChallengeFail)
        }

        return Outcome.Ok(Unit)
    }

    private suspend fun receiverHandleFlags(
        ctx: TransferReceiveContext
    ): Outcome<Unit, FileTransferStopReason> {
        val senderFlags = TransferFlags.fromBytes(ctx.socketReadChannel.readByte())
        val receiverFlags = TransferFlags(useEncryption = getUseEncryption())
        ctx.socketWriteChannel.writeByte(TransferFlags.toBytes(receiverFlags))
        ctx.socketWriteChannel.flush()
        if (senderFlags.useEncryption || receiverFlags.useEncryption) ctx.useEncryption = true
        return Outcome.Ok(Unit)
    }

    private suspend fun receiverHandleEncryptionHandshake(
        ctx: TransferReceiveContext
    ): Outcome<Unit, FileTransferStopReason> {
        val senderPublicKey = ByteArray(256)
        val iv = ByteArray(16)
        val salt = ByteArray(32)
        ctx.socketReadChannel.readFully(senderPublicKey)
        ctx.socketReadChannel.readFully(iv)
        ctx.socketReadChannel.readFully(salt)

        ctx.socketWriteChannel.writeFully(DHKey.keyToBytes(publicKey.await()))
        ctx.socketWriteChannel.flush()

        val sharedSecret = DHKey.computeSharedKey(DHKey.keyFromBytes(senderPublicKey), privateKey.await())
        ctx.encryptionKey = DHKey.hkdfExtract(DHKey.keyToBytes(sharedSecret), salt)
        ctx.encryptionIv = iv

        return Outcome.Ok(Unit)
    }

    private suspend fun receiverReadHeader(ctx: TransferReceiveContext): FileTransferRequestHeader {
        // Read Fixed Header
        val fixedHeaderBuffer = ctx.readSegment(FIXED_HEADER_SIZE)
        val command = fixedHeaderBuffer.readByte()
        val ipAddress = ByteArray(17).apply { fixedHeaderBuffer.readFully(this) }.toIPString()
        val port = ByteArray(2).apply { fixedHeaderBuffer.readFully(this) }.toUShort()
        val platform = fixedHeaderBuffer.readByte().toDevicePlatform()
        val senderNameLength = fixedHeaderBuffer.readByte().toInt()
        val fileNameLength = fixedHeaderBuffer.readShort().toInt()
        val payloadLength = fixedHeaderBuffer.readLong()
        fixedHeaderBuffer.readFully(ByteArray(24)) // Future use

        val senderName: String
        val fileName: String

        // Read Dynamic Header
        if (command == FileTransferCommand.Connect) {
            senderName = ""
            fileName = ""
        } else {
            val dynamicHeaderBuffer = ctx.readSegment(senderNameLength + fileNameLength)
            senderName = ByteArray(senderNameLength).apply { dynamicHeaderBuffer.readFully(this) }.decodeToString()
            fileName = ByteArray(fileNameLength).apply { dynamicHeaderBuffer.readFully(this) }.decodeToString()
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
        val fixedBytes = byteArrayOf(Platform.current.toDevicePlatform().toByte(), deviceName.length.toByte())
        ctx.writeSegment(fixedBytes)
        ctx.writeSegment(deviceName.encodeToByteArray())
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

        val buffer = Buffer()
        buffer.writeByte(responseByte)
        buffer.writeLong(response.existingFileLength)
        ctx.writeSegment(buffer.readByteArray())

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

    private suspend fun receiverReceiveFile(
        ctx: TransferReceiveContext,
        header: FileTransferRequestHeader,
        response: FileTransferResponse,
        setFileWriter: (BufferedSink) -> Unit,
    ): Outcome<Unit, FileTransferStopReason> {
        val sink = response.sink ?: return Outcome.Error(FileTransferStopReason.UnableToOpenFile)

        val event = FileTransferServerEvent.TransferProgress(ctx.transferId, 0, header.payloadLength)
        ctx.eventChannel.send(event)

        val fileWriter = sink.buffer()
        setFileWriter(fileWriter)
        var totalWritten = response.existingFileLength

        return coroutineScope {
            val notifyJob = launch {
                while (isActive) {
                    delay(100)
                    val progressEvent = FileTransferServerEvent.TransferProgress(ctx.transferId, totalWritten, header.payloadLength)
                    ctx.eventChannel.send(progressEvent)
                }
            }

            while (isActive) {
                if (totalWritten == header.payloadLength) {
                    ctx.eventChannel.send(FileTransferServerEvent.TransferComplete(ctx.transferId))
                    break
                }

                try {
                    ctx.socketReadChannel.readFully(ctx.readBuffer)
                } catch (e: ClosedReceiveChannelException) {
                    notifyJob.cancel()
                    val stopReason = when (val command = getReadBufferCancellationCommand(ctx)) {
                        null -> FileTransferStopReason.SocketClosed
                        else -> FileTransferStopReason.UserCancelled(command)
                    }
                    return@coroutineScope Outcome.Error(stopReason)
                }

                val actualBytes = if (ctx.useEncryption) {
                    AES.decryptAesCtr(ctx.readBuffer, ctx.encryptionKey, ctx.encryptionIv, CipherPadding.NoPadding)
                } else {
                    ctx.readBuffer
                }

                val chunkSize = minOf(actualBytes.size.toLong(), (header.payloadLength - totalWritten))
                fileWriter.write(actualBytes, 0, chunkSize.toInt())
                totalWritten += chunkSize
            }

            notifyJob.cancel()
            Outcome.Ok(Unit)
        }
    }

    private fun getReadBufferCancellationCommand(ctx: TransferReceiveContext): Command? {
        return try {
            val commandSignalIndex = ctx.readBuffer.find(commandSignalBytes)
            if (commandSignalIndex == -1) return null

            val command = Command.fromByte(ctx.readBuffer[commandSignalIndex + commandSignalBytes.size])
            if (command == Command.Unknown) return null
            command
        } catch (e: Exception) {
            null
        }
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

            withTimeout(3_000) {
                senderSendProtocol(transferContext)
                senderHandleAuth(transferContext)
                senderSendFlags(transferContext)
                if (transferContext.useEncryption) senderHandleEncryptionHandshake(transferContext)
                senderSendHeader(transferContext, file)
            }

            val existingFileLength = senderAwaitResponse(transferContext).unwrapOrReturn { return@sendJob this }

            val recipientCommandListener = senderAwaitCommand(transferContext) { command ->
                when (command) {
                    Command.CancelStop,
                    Command.CancelDelete -> {
                        send(FileTransferClientEvent.TransferStopped(transferId, FileTransferStopReason.UserCancelled(command)))
                        this@sendJob.cancel()
                    }
                    else -> {}
                }
            }

            senderSendFile(transferContext, file, existingFileLength) { outerFileReader = it }

            recipientCommandListener.cancel()
            Outcome.Ok(Unit)
        }

        val commandListenerJob = launch {
            val signal = commandFlow.first { it.transferId == transferId }
            when (signal.command) {
                Command.CancelStop,
                Command.CancelDelete -> sendJob.cancel(LANdTransferCancelledException(signal.command))
                else -> {}
            }
        }

        sendJob.awaitOutcome().runOnError { error ->
            when {
                error == Unit -> { /* Do Nothing */ }
                error is LANdTransferCancelledException -> {
                    outerSocketWriteChannel?.writeFully(commandSignalBytes + error.command.toByte())
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

        commandListenerJob.cancel()
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

    private suspend fun senderSendFlags(ctx: TransferSendContext) {
        val senderFlags = TransferFlags(useEncryption = getUseEncryption())
        ctx.socketWriteChannel.writeByte(TransferFlags.toBytes(senderFlags))
        ctx.socketWriteChannel.flush()

        val receiverFlags = TransferFlags.fromBytes(ctx.socketReadChannel.readByte())
        if (senderFlags.useEncryption || receiverFlags.useEncryption) ctx.useEncryption = true
        return
    }

    private suspend fun senderHandleEncryptionHandshake(ctx: TransferSendContext) {
        val iv = SecureRandom.nextBytes(16)
        val salt = SecureRandom.nextBytes(32)
        val receiverPublicKey = ByteArray(256)

        ctx.socketWriteChannel.writeFully(DHKey.keyToBytes(publicKey.await()))
        ctx.socketWriteChannel.writeFully(iv)
        ctx.socketWriteChannel.writeFully(salt)
        ctx.socketWriteChannel.flush()

        ctx.socketReadChannel.readFully(receiverPublicKey)

        val sharedSecret = DHKey.computeSharedKey(DHKey.keyFromBytes(receiverPublicKey), privateKey.await())
        ctx.encryptionKey = DHKey.hkdfExtract(DHKey.keyToBytes(sharedSecret), salt)
        ctx.encryptionIv = iv
    }

    private suspend fun senderSendHeader(ctx: TransferSendContext, file: FileTransferRequest) {
        // Send Fixed Header
        val fixedHeaderBuffer = Buffer()
        fixedHeaderBuffer.writeByte(FileTransferCommand.FileTransfer.toInt()) // Command
        fixedHeaderBuffer.write(getLocalIpAddress().toIPBytes())
        fixedHeaderBuffer.writeShort(FILE_TRANSFER_PORT)
        fixedHeaderBuffer.writeByte(Platform.current.toDevicePlatform().toByte().toInt())
        fixedHeaderBuffer.writeByte(file.senderName.length) // Sender Name Length
        fixedHeaderBuffer.writeShort(file.fileName.length) // File Name Length
        fixedHeaderBuffer.writeLong(file.length) // Payload Length
        fixedHeaderBuffer.write(ByteArray(24) { 0x00 }) // Future use
        ctx.writeSegment(fixedHeaderBuffer.readByteArray())

        // Send Dynamic Header
        val dynamicHeaderBuffer = Buffer()
        dynamicHeaderBuffer.write(file.senderName.encodeToByteArray())
        dynamicHeaderBuffer.write(file.fileName.encodeToByteArray())
        ctx.writeSegment(dynamicHeaderBuffer.readByteArray())
    }

    private suspend fun ProducerScope<FileTransferClientEvent>.senderAwaitResponse(
        ctx: TransferSendContext
    ): Outcome<Long, Unit> {
        send(FileTransferClientEvent.AwaitingAcceptance(ctx.transferId))
        val responseBuffer = ctx.readSegment(1 + 8)
        val response = responseBuffer.readByte().toInt()
        val existingFileLength = responseBuffer.readLong()

        if (response == 0x01) {
            send(FileTransferClientEvent.TransferResponseReceived(ctx.transferId, FileTransferResponseType.Accepted))
        } else if (response == 0x00) {
            send(FileTransferClientEvent.TransferResponseReceived(ctx.transferId, FileTransferResponseType.Rejected))
            return Outcome.Error(Unit)
        }

        return Outcome.Ok(existingFileLength)
    }

    private fun ProducerScope<FileTransferClientEvent>.senderAwaitCommand(
        ctx: TransferSendContext,
        onCommand: suspend (Command) -> Unit,
    ) = asyncOutcome {
        val commandSignalBuffer = ByteArray(commandSignalBytes.size + 1)
        while (isActive) {
            ctx.socketReadChannel.readAvailable(commandSignalBuffer)
            if (!commandSignalBuffer.offsetContentEquals(commandSignalBytes, 0)) continue
            onCommand(Command.fromByte(commandSignalBuffer.last()))
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

        coroutineScope {
            var eof = false
            val notifyJob = launch {
                while (isActive) {
                    delay(100)
                    send(FileTransferClientEvent.TransferProgress(ctx.transferId, totalRead, file.length))
                }
            }

            while (isActive && !eof) {
                var offset = 0

                while (isActive && offset < bufferSize) {
                    val read = fileReader.read(ctx.readBuffer, offset, bufferSize - offset)
                    if (read == -1) {
                        eof = true
                        break
                    }
                    offset += read
                }

                val readBytes = if (ctx.useEncryption) {
                    AES.encryptAesCtr(ctx.readBuffer, ctx.encryptionKey, ctx.encryptionIv, CipherPadding.NoPadding)
                } else {
                    ctx.readBuffer
                }

                ctx.socketWriteChannel.writeFully(readBytes, 0, bufferSize)
                totalRead += offset
            }

            notifyJob.cancel()
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

private suspend inline fun IEncryptorContext.readSegment(size: Int): Buffer {
    val rawBytes = ByteArray(size).apply { socketReadChannel.readFully(this) }
    val buffer = Buffer()

    val actualBytes = if (useEncryption) {
        AES.decryptAesCtr(rawBytes, encryptionKey, encryptionIv, CipherPadding.NoPadding)
    } else {
        rawBytes
    }

    buffer.write(actualBytes)
    return buffer
}

private suspend inline fun IEncryptorContext.writeSegment(bytes: ByteArray) {
    val actualBytes = if (useEncryption) {
        AES.encryptAesCtr(bytes, encryptionKey, encryptionIv, CipherPadding.NoPadding)
    } else {
        bytes
    }

    socketWriteChannel.writeFully(actualBytes)
    socketWriteChannel.flush()
}

private fun calculateAuthResponse(random: ByteArray): ByteArray {
    val hashedBytes = random.sha256().bytes
    for (i in hashedBytes.indices) {
        hashedBytes[i] = hashedBytes[i] xor AUTH_CHALLENGE_XOR[i].toByte()
    }
    return hashedBytes
}

expect fun FileTransferService.isClosedConnectionException(exception: Any): Boolean

private fun DevicePlatform.toByte(): Byte = when(this) {
    DevicePlatform.MacOS -> 0x00
    DevicePlatform.Windows -> 0x01
    DevicePlatform.Linux -> 0x02
    DevicePlatform.iOS -> 0x03
    DevicePlatform.Android -> 0x04
    DevicePlatform.Unknown -> 0xFF.toByte()
}

private fun Byte.toDevicePlatform() = when(this) {
    0x00.toByte() -> DevicePlatform.MacOS
    0x01.toByte() -> DevicePlatform.Windows
    0x02.toByte() -> DevicePlatform.Linux
    0x03.toByte() -> DevicePlatform.iOS
    0x04.toByte() -> DevicePlatform.Android
    else -> DevicePlatform.Unknown
}

fun Command.toByte() = when (this) {
    Command.CancelStop -> 0x00.toByte()
    Command.CancelDelete -> 0x01.toByte()
    Command.Unknown -> 0xFF.toByte()
}

fun Command.Companion.fromByte(value: Byte) = when (value) {
    0x00.toByte() -> Command.CancelStop
    0x01.toByte() -> Command.CancelDelete
    else -> Command.Unknown
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
private fun String?.toIPBytes(): ByteArray {
    if (this == null) return ByteArray(16)
    return byteArrayOf(0x00) + split(".").map { it.toUInt().toByte() }.take(4).toByteArray() + ByteArray(12)
}