package com.ethossoftworks.land.service.filetransfer

import com.ethossoftworks.land.entity.Device
import com.ethossoftworks.land.entity.DevicePlatform
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.flow.Flow
import okio.Sink
import okio.Source

interface IFileTransferService: IFileTransferServer, IFileTransferClient {
    suspend fun sendCommandSignal(transferId: Short, command: Command)
}

enum class Command {
    CancelStop,
    CancelDelete,
    Unknown;

    companion object
}

interface IFileTransferServer {
    suspend fun startServer(): Flow<FileTransferServerEvent>
    suspend fun respondToTransferRequest(
        transferId: Short,
        existingFileLength: Long,
        response: FileTransferResponseType,
        sink: Sink?,
    )
}

interface IFileTransferClient {
    suspend fun sendFile(file: FileTransferRequest, destinationIp: String): Flow<FileTransferClientEvent>
    suspend fun testConnection(destinationIp: String): Outcome<Device, Exception>
}

data class FileTransferRequest(
    val senderName: String,
    val fileName: String,
    val length: Long,
    val source: Source,
)

data class FileTransferResponse(
    val transferId: Short,
    val responseType: FileTransferResponseType,
    val existingFileLength: Long,
    val sink: Sink?,
)

enum class FileTransferResponseType {
    Accepted,
    Rejected,
}

sealed class FileTransferServerEvent {
    data object ServerStarted: FileTransferServerEvent()

    data class ServerStopped(val error: Any?): FileTransferServerEvent()

    data class TransferRequested(
        val transferId: Short,
        val senderName: String,
        val senderPlatform: DevicePlatform,
        val senderIPAddress: String,
        val senderPort: UShort,
        val fileName: String,
        val length: Long,
    ): FileTransferServerEvent()

    data class TransferProgress(
        val transferId: Short,
        val bytesReceived: Long,
        val bytesTotal: Long,
    ): FileTransferServerEvent()

    data class TransferStopped(
        val transferId: Short,
        val reason: FileTransferStopReason
    ): FileTransferServerEvent()

    data class TransferComplete(val transferId: Short): FileTransferServerEvent()
}

sealed class FileTransferStopReason {
    data object AuthorizationChallengeFail: FileTransferStopReason()
    data object UnableToOpenFile: FileTransferStopReason()
    data object SocketClosed: FileTransferStopReason()
    data object UnknownProtocol: FileTransferStopReason()
    data class UserCancelled(
        val command: Command,
        val cancelledByLocalUser: Boolean = false,
    ): FileTransferStopReason()
    data object Unknown: FileTransferStopReason()
}

sealed class FileTransferClientEvent {
    data class Connecting(val transferId: Short): FileTransferClientEvent()
    data class AwaitingAcceptance(val transferId: Short): FileTransferClientEvent()

    data class TransferResponseReceived(
        val transferId: Short,
        val response: FileTransferResponseType,
    ): FileTransferClientEvent()

    data class TransferProgress(
        val transferId: Short,
        val bytesSent: Long,
        val bytesTotal: Long,
    ): FileTransferClientEvent()

    data class TransferStopped(
        val transferId: Short,
        val reason: FileTransferStopReason
    ): FileTransferClientEvent()

    data class TransferComplete(val transferId: Short): FileTransferClientEvent()
}

data class FileTransferRequestHeader(
    val command: Byte,
    val ipAddress: String,
    val port: UShort,
    val platform: DevicePlatform,
    val senderName: String,
    val fileName: String,
    val payloadLength: Long,
)

object FileTransferCommand {
    const val Connect = 0x00.toByte()
    const val FileTransfer = 0x01.toByte()
}