package com.ethossoftworks.land.common.interactor.filetransfer

import com.ethossoftworks.land.common.interactor.preferences.AppPreferencesInteractor
import com.ethossoftworks.land.common.model.device.Device
import com.ethossoftworks.land.common.service.file.FileWriteMode
import com.ethossoftworks.land.common.service.file.IFileHandler
import com.ethossoftworks.land.common.service.filetransfer.*
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.launch
import kotlin.math.round
import kotlin.math.roundToInt

data class FileTransferState(
    val isServerRunning: Boolean = false,
    val deviceNameRequestIdMap: Map<String, List<Short>> = LinkedHashMap(),
    val activeRequests: Map<Short, FileTransfer> = LinkedHashMap(),
    val transferMessageQueue: List<FileTransfer> = emptyList(),
)

data class FileTransfer(
    val requestId: Short,
    val deviceName: String = "",
    val fileName: String = "",
    val bytesTransferred: Long = 0,
    val bytesTotal: Long = 0,
    val bytesExisting: Long = 0,
    val direction: FileTransferDirection = FileTransferDirection.Sending,
    val stopReason: FileTransferStopReason? = null,
    val status: FileTransferStatus = FileTransferStatus.AwaitingAcceptance,
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

    suspend fun startServer() {
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
                                        requestId = it.requestId,
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
                                activeRequests = state.activeRequests.toMutableMap().apply {
                                    val request = this[it.requestId] ?: return@apply
                                    put(it.requestId, request.copy(bytesTransferred = it.bytesReceived))
                                }
                            )
                        }
                    }
                    is FileTransferServerEvent.TransferComplete -> {
                        update { state ->
                            val sender = state.activeRequests[it.requestId]?.deviceName ?: return@update state

                            state.copy(
                                activeRequests = state.activeRequests.toMutableMap().apply { remove(it.requestId) },
                                deviceNameRequestIdMap = state.deviceNameRequestIdMap.toMutableMap().apply {
                                    put(sender, (this[sender] ?: emptyList()) - it.requestId)
                                },
                            )
                        }
                    }
                    is FileTransferServerEvent.TransferStopped -> {
                        update { state ->
                            val request = state.activeRequests[it.requestId]?.copy(stopReason = it.reason) ?: return@update state

                            state.copy(
                                activeRequests = state.activeRequests.toMutableMap().apply { remove(request.requestId) },
                                transferMessageQueue = state.transferMessageQueue + request.copy(status = FileTransferStatus.Stopped),
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun sendFile(device: Device, file: FileTransferRequest) {
        interactorScope.launch {
            fileTransferService.sendFile(file, device.ipAddress).collect { event ->
                when (event) {
                    is FileTransferClientEvent.AwaitingAcceptance -> {
                        val transfer = FileTransfer(
                            requestId = event.requestId,
                            fileName = file.fileName,
                            deviceName = device.name,
                            bytesTotal = file.length,
                            direction = FileTransferDirection.Sending,
                        )

                        update { state ->
                            state.copy(
                                deviceNameRequestIdMap = state.deviceNameRequestIdMap.toMutableMap().apply {
                                    put(device.name, (this[device.name] ?: emptyList()) + event.requestId)
                                },
                                activeRequests = state.activeRequests.toMutableMap().apply {
                                    put(transfer.requestId, transfer)
                                }
                            )
                        }
                    }
                    is FileTransferClientEvent.TransferResponseReceived -> {
                        update { state ->
                            val request = state.activeRequests[event.requestId] ?: return@update state

                            state.copy(
                                transferMessageQueue = if (event.response == FileTransferResponseType.Rejected) {
                                    state.transferMessageQueue + request.copy(status = FileTransferStatus.Rejected)
                                } else {
                                    state.transferMessageQueue
                                },
                                activeRequests = if (event.response == FileTransferResponseType.Rejected) {
                                    state.activeRequests.toMutableMap().apply { remove(event.requestId) }
                                } else {
                                    state.activeRequests.toMutableMap().apply {
                                        this[event.requestId] = request.copy(status = FileTransferStatus.Progress)
                                    }
                                },
                            )
                        }
                    }
                    is FileTransferClientEvent.TransferProgress -> {
                        update { state ->
                            state.copy(
                                activeRequests = state.activeRequests.toMutableMap().apply {
                                    val request = this[event.requestId] ?: return@apply
                                    put(event.requestId, request.copy(bytesTransferred = event.bytesSent))
                                }
                            )
                        }
                    }
                    is FileTransferClientEvent.TransferComplete -> {
                        update { state ->
                            state.copy(
                                activeRequests = state.activeRequests.toMutableMap().apply { remove(event.requestId) },
                            )
                        }
                    }
                    is FileTransferClientEvent.TransferStopped -> {
                        val request = state.activeRequests[event.requestId] ?: return@collect

                        update { state ->
                            state.copy(
                                activeRequests = state.activeRequests.toMutableMap().apply { remove(event.requestId) },
                                transferMessageQueue = state.transferMessageQueue + request.copy(
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

    suspend fun respondToRequest(
        request: FileTransfer,
        response: FileTransferResponseType,
        mode: FileWriteMode
    ): Outcome<Unit, Any> {
        // TODO: Implement better early return errors
        val saveFolder = appPreferencesInteractor.state.saveFolder ?: return Outcome.Error(Unit)
        val sink = if (response == FileTransferResponseType.Accepted) {
            val sinkOutcome = fileHandler.openFileToWrite(saveFolder, request.fileName, mode)
            if (sinkOutcome !is Outcome.Ok) return Outcome.Error(Unit)
            sinkOutcome.value
        } else {
            null
        }

        update { state ->
            val pendingRequest = state.transferMessageQueue.firstOrNull { it.requestId == request.requestId } ?: return@update state

            state.copy(
                activeRequests = if (response == FileTransferResponseType.Accepted) {
                    state.activeRequests.toMutableMap().apply {
                        put(request.requestId, pendingRequest.copy(status = FileTransferStatus.Progress))
                    }
                } else {
                    state.activeRequests
                },
                transferMessageQueue = state.transferMessageQueue - pendingRequest,
                deviceNameRequestIdMap = state.deviceNameRequestIdMap.toMutableMap().apply {
                    put(request.deviceName, (this[request.deviceName] ?: emptyList()) + request.requestId)
                }
            )
        }

        fileTransferService.respondToTransferRequest(
            requestId = request.requestId,
            existingFileLength = when (mode) {
                FileWriteMode.Append -> request.bytesExisting
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
                deviceNameRequestIdMap = state.deviceNameRequestIdMap.toMutableMap().apply {
                    put(item.deviceName, (this[item.deviceName] ?: emptyList()) - item.requestId)
                }
            )
        }
    }
}