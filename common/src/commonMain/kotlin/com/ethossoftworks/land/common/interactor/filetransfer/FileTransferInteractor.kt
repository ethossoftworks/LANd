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
    val deviceNameTransferIdMap: Map<String, List<Short>> = LinkedHashMap(),
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
            fileTransferService.startServer().collect {
                when (it) {
                    is FileTransferServerEvent.ServerStarted -> update { state -> state.copy(isServerRunning = true) }
                    is FileTransferServerEvent.ServerStopped -> update { state -> state.copy(isServerRunning = false) }
                    is FileTransferServerEvent.TransferRequested -> {
                        // TODO: Implement better early return handling
                        val saveFolder = appPreferencesInteractor.state.saveFolder ?: return@collect
                        val metadataOutcome = fileHandler.readFileMetadata(saveFolder, it.fileName)

                        update { state ->
                            state.copy(
                                transferMessageQueue = state.transferMessageQueue +
                                    FileTransfer(
                                        transferId = it.transferId,
                                        fileName = it.fileName,
                                        deviceName = it.senderName,
                                        bytesTotal = it.length,
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
                                    val transfer = this[it.transferId] ?: return@apply
                                    put(it.transferId, transfer.copy(bytesTransferred = it.bytesReceived))
                                }
                            )
                        }
                    }
                    is FileTransferServerEvent.TransferComplete -> {
                        update { state ->
                            val sender = state.activeTransfers[it.transferId]?.deviceName ?: return@update state

                            state.copy(
                                activeTransfers = state.activeTransfers.toMutableMap().apply { remove(it.transferId) },
                                deviceNameTransferIdMap = state.deviceNameTransferIdMap.toMutableMap().apply {
                                    put(sender, (this[sender] ?: emptyList()) - it.transferId)
                                },
                            )
                        }
                    }
                    is FileTransferServerEvent.TransferStopped -> {
                        update { state ->
                            val transfer = state.activeTransfers[it.transerId]?.copy(stopReason = it.reason) ?: return@update state

                            state.copy(
                                activeTransfers = state.activeTransfers.toMutableMap().apply { remove(transfer.transferId) },
                                transferMessageQueue = state.transferMessageQueue + transfer.copy(status = FileTransferStatus.Stopped),
                            )
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
                                deviceNameTransferIdMap = state.deviceNameTransferIdMap.toMutableMap().apply {
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
                                transferMessageQueue = if (event.response == FileTransferResponseType.Rejected) {
                                    state.transferMessageQueue + transfer.copy(status = FileTransferStatus.Rejected)
                                } else {
                                    state.transferMessageQueue
                                },
                                activeTransfers = if (event.response == FileTransferResponseType.Rejected) {
                                    state.activeTransfers.toMutableMap().apply { remove(event.transferId) }
                                } else {
                                    state.activeTransfers.toMutableMap().apply {
                                        this[event.transferId] = transfer.copy(status = FileTransferStatus.Progress)
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
                                transferMessageQueue = state.transferMessageQueue + transfer.copy(
                                    stopReason = event.reason,
                                    status = FileTransferStatus.Stopped,
                                ),
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
    ): Outcome<Unit, Any> {
        // TODO: Implement better early return errors
        val saveFolder = appPreferencesInteractor.state.saveFolder ?: return Outcome.Error(Unit)
        val sink = if (response == FileTransferResponseType.Accepted) {
            val sinkOutcome = fileHandler.openFileToWrite(saveFolder, transfer.fileName, mode)
            if (sinkOutcome !is Outcome.Ok) return Outcome.Error(Unit)
            sinkOutcome.value
        } else {
            null
        }

        update { state ->
            val pendingTransfer = state.transferMessageQueue.firstOrNull { it.transferId == transfer.transferId } ?: return@update state

            state.copy(
                activeTransfers = if (response == FileTransferResponseType.Accepted) {
                    state.activeTransfers.toMutableMap().apply {
                        put(transfer.transferId, pendingTransfer.copy(status = FileTransferStatus.Progress))
                    }
                } else {
                    state.activeTransfers
                },
                transferMessageQueue = state.transferMessageQueue - pendingTransfer,
                deviceNameTransferIdMap = state.deviceNameTransferIdMap.toMutableMap().apply {
                    put(transfer.deviceName, (this[transfer.deviceName] ?: emptyList()) + transfer.transferId)
                }
            )
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

        return Outcome.Ok(Unit)
    }

    fun transferMessageQueueItemHandled(item: FileTransfer) {
        update { state ->
            state.copy(
                transferMessageQueue = state.transferMessageQueue - item,
                deviceNameTransferIdMap = state.deviceNameTransferIdMap.toMutableMap().apply {
                    put(item.deviceName, (this[item.deviceName] ?: emptyList()) - item.transferId)
                }
            )
        }
    }
}