package com.ethossoftworks.land.common.service.filetransfer

import kotlinx.coroutines.flow.Flow
import okio.Source

interface IFileTransferService {
    suspend fun startServer(): Flow<FileTransferServerEvent>
    suspend fun sendFile(file: FileTransfer, destinationIp: String): Flow<FileTransferProgress>
}

data class FileTransfer(
    val senderName: String,
    val name: String,
    val length: Long,
    val source: Source,
)

sealed class FileTransferServerEvent {
    object Idle: FileTransferServerEvent()
    object ServerStarted: FileTransferServerEvent()
    data class ServerStopped(val error: Any?): FileTransferServerEvent()
    data class TransferRequested(
        val requestId: Int,
        val senderName: String,
        val fileName: String,
        val length: Long,
    )
    data class TransferRejected(
        val requestId: Int,
        val reason: FileTransferRejectReason,
    )
    data class TransferProgress(
        val requestId: Int,
        val bytesSent: Long,
        val totalBytes: Long,
    ): FileTransferServerEvent()
    data class TransferStopped(
        val requestId: Int,
        val reason: FileTransferStopReason
    )
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
        val requestId: Int,
        val reason: FileTransferStopReason
    ): FileTransferProgress()
    data class TransferProgress(
        val requestId: Int,
        val bytesSent: Long,
        val totalBytes: Long,
    ): FileTransferProgress()
    data class TransferComplete(val requestId: Int): FileTransferProgress()
}