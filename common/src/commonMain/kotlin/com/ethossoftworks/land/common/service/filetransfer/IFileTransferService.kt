package com.ethossoftworks.land.common.service.filetransfer

import kotlinx.coroutines.flow.Flow
import okio.Source

interface IFileTransferService: IFileTransferServer, IFileTransferClient

interface IFileTransferServer {
    suspend fun startServer(): Flow<FileTransferServerEvent>
    suspend fun respondToTransferRequest(requestId: Short, response: FileTransferResponse)
}

interface IFileTransferClient {
    suspend fun sendFile(file: FileTransfer, destinationIp: String): Flow<FileTransferProgress>
}

data class FileTransfer(
    val senderName: String,
    val name: String,
    val length: Long,
    val source: Source,
)

enum class FileTransferResponse {
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

    data class TransferRejected(
        val requestId: Short,
        val reason: FileTransferRejectReason,
    ): FileTransferServerEvent()

    data class TransferProgress(
        val requestId: Short,
        val bytesSent: Long,
        val totalBytes: Long,
    ): FileTransferServerEvent()

    data class TransferStopped(
        val requestId: Short,
        val reason: FileTransferStopReason
    ): FileTransferServerEvent()

    data class TransferComplete(val requestId: Int)
}

enum class FileTransferStopReason {
    CancelledByClient,
    Unknown,
}

enum class FileTransferRejectReason {
    RejectedByClient,
    AuthorizationChallengeFail,
    Unknown,
}

sealed class FileTransferProgress {
    data class TransferStopped(
        val requestId: Short,
        val reason: FileTransferStopReason
    ): FileTransferProgress()

    data class TransferProgress(
        val requestId: Short,
        val bytesSent: Long,
        val totalBytes: Long,
    ): FileTransferProgress()

    data class TransferComplete(val requestId: Int): FileTransferProgress()
}