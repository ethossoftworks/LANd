package com.ethossoftworks.land.common.service.filetransfer

import kotlinx.coroutines.flow.Flow
import okio.Sink
import okio.Source

interface IFileTransferService: IFileTransferServer, IFileTransferClient

interface IFileTransferServer {
    suspend fun startServer(): Flow<FileTransferServerEvent>
    suspend fun respondToTransferRequest(
        requestId: Short,
        existingFileLength: Long,
        response: FileTransferResponseType,
        sink: Sink?,
    )
}

interface IFileTransferClient {
    suspend fun sendFile(file: FileTransfer, destinationIp: String): Flow<FileTransferClientEvent>
}

data class FileTransfer(
    val senderName: String,
    val name: String,
    val length: Long,
    val source: Source,
)


data class FileTransferResponse(
    val requestId: Short,
    val responseType: FileTransferResponseType,
    val existingFileLength: Long,
    val sink: Sink?,
)

enum class FileTransferResponseType {
    Accepted,
    Rejected,
}

sealed class FileTransferServerEvent {
    object Idle: FileTransferServerEvent()

    object ServerStarted: FileTransferServerEvent()

    data class ServerStopped(val error: Any?): FileTransferServerEvent()

    data class TransferRequested(
        val requestId: Short,
        val senderName: String,
        val fileName: String,
        val length: Long,
    ): FileTransferServerEvent()

    data class TransferProgress(
        val requestId: Short,
        val bytesReceived: Long,
        val totalBytes: Long,
    ): FileTransferServerEvent()

    data class TransferStopped(
        val requestId: Short,
        val reason: FileTransferStopReason
    ): FileTransferServerEvent()

    data class TransferComplete(val requestId: Short): FileTransferServerEvent()
}

enum class FileTransferStopReason {
    AuthorizationChallengeFail,
    CancelledByClient,
    UnableToOpenFile,
    Unknown,
}

sealed class FileTransferClientEvent {
    object TransferRejected: FileTransferClientEvent()

    data class TransferProgress(
        val requestId: Short,
        val bytesSent: Long,
        val totalBytes: Long,
    ): FileTransferClientEvent()

    data class TransferStopped(
        val requestId: Short,
        val reason: FileTransferStopReason
    ): FileTransferClientEvent()

    data class TransferComplete(val requestId: Short): FileTransferClientEvent()
}