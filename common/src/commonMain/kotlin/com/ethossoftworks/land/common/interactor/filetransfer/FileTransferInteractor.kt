package com.ethossoftworks.land.common.interactor.filetransfer

import com.ethossoftworks.land.common.interactor.preferences.AppPreferencesInteractor
import com.ethossoftworks.land.common.model.device.Device
import com.ethossoftworks.land.common.service.file.FileWriteMode
import com.ethossoftworks.land.common.service.file.IFileHandler
import com.ethossoftworks.land.common.service.filetransfer.*
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.math.round
import kotlin.math.roundToInt

data class FileTransferState(
    val isServerRunning: Boolean = false,
    val deviceToTransferIdMap: Map<String, List<Short>> = LinkedHashMap(),
    val activeTransfers: Map<Short, FileTransfer> = LinkedHashMap(),
    val transferMessageQueue: List<FileTransfer> = emptyList(),
)

data class FileTransfer(
    val transferId: Short,
    val deviceName: String = "",
    val fileName: String = "",
    val bytesTransferred: Long = 0,
    val bytesTotal: Long = 0,
    val bytesExisting: Long = 0,
    val direction: FileTransferDirection = FileTransferDirection.Sending,
    val stopReason: FileTransferStopReason? = null,
    val status: FileTransferStatus = FileTransferStatus.Connecting,
) {
    fun sizeString(): String {
        return when {
            bytesTotal >= 1_000_000_000 -> "${round((bytesTotal / 1_000_000_000f) * 100f) / 100} GB"
            bytesTotal >= 1_000_000 -> "${round((bytesTotal / 1_000_000f) * 100f) / 100} MB"
            bytesTotal >= 1_000 -> "${(bytesTotal / 1_000f).roundToInt()} KB"
            else -> "$bytesTotal bytes"
        }
    }
}

enum class FileTransferStatus {
    Connecting,
    AwaitingAcceptance,
    Rejected,
    Progress,
    Stopped,
}

enum class FileTransferDirection {
    Sending,
    Receiving,
}

class FileTransferInteractor(
    private val fileTransferService: IFileTransferService,
    private val appPreferencesInteractor: AppPreferencesInteractor,
    private val fileHandler: IFileHandler,
): Interactor<FileTransferState>(
    initialState = FileTransferState(),
) {

    private val serverJob = atomic<Job?>(null)

    suspend fun startServer() {
        stopServer()
        interactorScope.launch {
            fileTransferService.startServer().collect { event ->
                when (event) {
                    is FileTransferServerEvent.ServerStarted -> update { state -> state.copy(isServerRunning = true) }
                    is FileTransferServerEvent.ServerStopped -> update { state -> state.copy(isServerRunning = false) }
                    is FileTransferServerEvent.TransferRequested -> {
                        // TODO: Implement better early return handling
                        val saveFolder = appPreferencesInteractor.state.saveFolder ?: return@collect
                        val metadataOutcome = fileHandler.readFileMetadata(saveFolder, event.fileName)

                        update { state ->
                            state.copy(
                                transferMessageQueue = state.transferMessageQueue +
                                    FileTransfer(
                                        transferId = event.transferId,
                                        fileName = event.fileName,
                                        deviceName = event.senderName,
                                        bytesTotal = event.length,
                                        bytesExisting = when (metadataOutcome) {
                                            is Outcome.Ok -> metadataOutcome.value.length
                                            else -> 0
                                        },
                                        direction = FileTransferDirection.Receiving,
                                        status = FileTransferStatus.AwaitingAcceptance,
                                    )
                            )
                        }
                    }
                    is FileTransferServerEvent.TransferProgress -> {
                        update { state ->
                            state.copy(
                                activeTransfers = state.activeTransfers.toMutableMap().apply {
                                    val transfer = this[event.transferId] ?: return@apply
                                    put(event.transferId, transfer.copy(bytesTransferred = event.bytesReceived))
                                }
                            )
                        }
                    }
                    is FileTransferServerEvent.TransferComplete -> {
                        update { state ->
                            val sender = state.activeTransfers[event.transferId]?.deviceName ?: return@update state

                            state.copy(
                                activeTransfers = state.activeTransfers.toMutableMap().apply { remove(event.transferId) },
                                deviceToTransferIdMap = state.deviceToTransferIdMap.toMutableMap().apply {
                                    put(sender, (this[sender] ?: emptyList()) - event.transferId)
                                },
                            )
                        }
                    }
                    is FileTransferServerEvent.TransferStopped -> {
                        val transfer = state.activeTransfers[event.transferId]?.copy(stopReason = event.reason) ?: return@collect

                        update { state ->
                            state.copy(
                                activeTransfers = state.activeTransfers.toMutableMap().apply { remove(transfer.transferId) },
                                transferMessageQueue = state.transferMessageQueue.toMutableList().apply {
                                    if (event.reason is FileTransferStopReason.Cancelled && event.reason.cancelledByLocalUser) return@apply
                                    add(transfer.copy(stopReason = event.reason, status = FileTransferStatus.Stopped))
                                }
                            )
                        }

                        if (event.reason is FileTransferStopReason.Cancelled && event.reason.command == CancellationCommand.Delete) {
                            val saveFolder = appPreferencesInteractor.state.saveFolder ?: return@collect
                            fileHandler.deleteFile(saveFolder, transfer.fileName)
                        }
                    }
                }
            }
        }.apply {
            serverJob.update { this }
        }
    }

    suspend fun stopServer() {
        serverJob.value?.cancelAndJoin()
        update { state -> state.copy(isServerRunning = false) }
    }

    suspend fun sendFile(device: Device, file: FileTransferRequest) {
        interactorScope.launch {
            fileTransferService.sendFile(file, device.ipAddress).collect { event ->
                when (event) {
                    is FileTransferClientEvent.Connecting -> {
                        val transfer = FileTransfer(
                            transferId = event.transferId,
                            fileName = file.fileName,
                            deviceName = device.name,
                            bytesTotal = file.length,
                            direction = FileTransferDirection.Sending,
                        )

                        update { state ->
                            state.copy(
                                deviceToTransferIdMap = state.deviceToTransferIdMap.toMutableMap().apply {
                                    put(device.name, (this[device.name] ?: emptyList()) + event.transferId)
                                },
                                activeTransfers = state.activeTransfers.toMutableMap().apply {
                                    put(transfer.transferId, transfer)
                                }
                            )
                        }
                    }
                    is FileTransferClientEvent.AwaitingAcceptance -> {
                        update { state ->
                            val transfer = state.activeTransfers[event.transferId] ?: return@update state

                            state.copy(
                                activeTransfers = state.activeTransfers.toMutableMap().apply {
                                    put(transfer.transferId, transfer.copy(status = FileTransferStatus.AwaitingAcceptance))
                                }
                            )
                        }
                    }
                    is FileTransferClientEvent.TransferResponseReceived -> {
                        update { state ->
                            val transfer = state.activeTransfers[event.transferId] ?: return@update state

                            state.copy(
                                transferMessageQueue = state.transferMessageQueue.toMutableList().apply {
                                    if (event.response == FileTransferResponseType.Accepted) return@apply
                                    add(transfer.copy(status = FileTransferStatus.Rejected))
                                },
                                activeTransfers = state.activeTransfers.toMutableMap().apply {
                                    if (event.response == FileTransferResponseType.Rejected) {
                                        remove(event.transferId)
                                    } else {
                                        put(event.transferId, transfer.copy(status = FileTransferStatus.Progress))
                                    }
                                },
                            )
                        }
                    }
                    is FileTransferClientEvent.TransferProgress -> {
                        update { state ->
                            state.copy(
                                activeTransfers = state.activeTransfers.toMutableMap().apply {
                                    val transfer = this[event.transferId] ?: return@apply
                                    put(event.transferId, transfer.copy(bytesTransferred = event.bytesSent))
                                }
                            )
                        }
                    }
                    is FileTransferClientEvent.TransferComplete -> {
                        update { state ->
                            state.copy(
                                activeTransfers = state.activeTransfers.toMutableMap().apply { remove(event.transferId) },
                            )
                        }
                    }
                    is FileTransferClientEvent.TransferStopped -> {
                        val transfer = state.activeTransfers[event.transferId] ?: return@collect

                        update { state ->
                            state.copy(
                                activeTransfers = state.activeTransfers.toMutableMap().apply { remove(event.transferId) },
                                transferMessageQueue = state.transferMessageQueue.toMutableList().apply {
                                    if (event.reason is FileTransferStopReason.Cancelled && event.reason.cancelledByLocalUser) return@apply
                                    add(transfer.copy(stopReason = event.reason, status = FileTransferStatus.Stopped))
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun respondToTransferRequest(
        transfer: FileTransfer,
        response: FileTransferResponseType,
        mode: FileWriteMode
    ) {
        update { state ->
            val pendingTransfer = state.transferMessageQueue.firstOrNull { it.transferId == transfer.transferId } ?: return@update state

            state.copy(
                activeTransfers = state.activeTransfers.toMutableMap().apply {
                    if (response == FileTransferResponseType.Rejected) return@apply
                    put(transfer.transferId, pendingTransfer.copy(status = FileTransferStatus.Progress))
                },
                transferMessageQueue = state.transferMessageQueue - pendingTransfer,
                deviceToTransferIdMap = state.deviceToTransferIdMap.toMutableMap().apply {
                    put(transfer.deviceName, (this[transfer.deviceName] ?: emptyList()) + transfer.transferId)
                }
            )
        }

        val saveFolder = appPreferencesInteractor.state.saveFolder
        if (saveFolder == null) {
            addTransferMessage(transfer.copy(status = FileTransferStatus.Stopped, stopReason = FileTransferStopReason.UnableToOpenFile))
            return
        }

        val sink = if (response == FileTransferResponseType.Accepted) {
            val sinkOutcome = fileHandler.openFileToWrite(saveFolder, transfer.fileName, mode)
            if (sinkOutcome !is Outcome.Ok) {
                addTransferMessage(transfer.copy(status = FileTransferStatus.Stopped, stopReason = FileTransferStopReason.UnableToOpenFile))
                return
            }
            sinkOutcome.value
        } else {
            null
        }

        fileTransferService.respondToTransferRequest(
            transferId = transfer.transferId,
            existingFileLength = when (mode) {
                FileWriteMode.Append -> transfer.bytesExisting
                else -> 0
            },
            response = response,
            sink = sink
        )
    }

    suspend fun cancelTransfer(
        transferId: Short,
        command: CancellationCommand = CancellationCommand.Stop,
    ) {
        val transfer = state.activeTransfers[transferId] ?: return
        fileTransferService.cancelTransfer(transfer.transferId, command)
    }

    suspend fun testConnection(ipAddress: String): Outcome<Device, Exception> =
        fileTransferService.testConnection(ipAddress)

    private fun addTransferMessage(transfer: FileTransfer) {
        update { state ->
            state.copy(
                activeTransfers = state.activeTransfers.toMutableMap().apply { remove(transfer.transferId) },
                transferMessageQueue = state.transferMessageQueue.toMutableList().apply { add(transfer) },
            )
        }
    }

    fun transferMessageHandled(item: FileTransfer) {
        update { state ->
            state.copy(
                transferMessageQueue = state.transferMessageQueue - item,
                deviceToTransferIdMap = state.deviceToTransferIdMap.toMutableMap().apply {
                    put(item.deviceName, (this[item.deviceName] ?: emptyList()) - item.transferId)
                }
            )
        }
    }
}