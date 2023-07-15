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
    val senderRequestIdMap: Map<String, List<Short>> = LinkedHashMap(),
    val pendingRequests: Map<Short, FileTransfer> = LinkedHashMap(),
    val activeRequests: Map<Short, FileTransfer> = LinkedHashMap(),
    val finishedRequests: Map<Short, FileTransfer> = LinkedHashMap(),
)

data class FileTransfer(
    val requestId: Short,
    val senderName: String = "",
    val fileName: String = "",
    val bytesTransferred: Long = 0,
    val bytesTotal: Long = 0,
    val bytesExisting: Long = 0,
    val direction: FileTransferDirection = FileTransferDirection.Sending,
    val stopReason: FileTransferStopReason? = null,
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
                                pendingRequests = state.pendingRequests.toMutableMap().apply {
                                    val fileTransfer = FileTransfer(
                                        requestId = it.requestId,
                                        fileName = it.fileName,
                                        senderName = it.senderName,
                                        bytesTotal = it.length,
                                        bytesExisting = when (metadataOutcome) {
                                            is Outcome.Ok -> metadataOutcome.value.length
                                            else -> 0
                                        },
                                        direction = FileTransferDirection.Receiving,
                                    )
                                    put(it.requestId, fileTransfer)
                                }
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
                            val sender = state.activeRequests[it.requestId]?.senderName ?: return@update state
                            val request = state.activeRequests[it.requestId] ?: return@update state

                            state.copy(
                                activeRequests = state.activeRequests.toMutableMap().apply { remove(it.requestId) },
                                senderRequestIdMap = state.senderRequestIdMap.toMutableMap().apply {
                                    val existingMap = this[sender] ?: emptyList()
                                    put(sender, existingMap.toMutableList().apply { remove(it.requestId) })
                                },
                                finishedRequests = state.finishedRequests.toMutableMap().apply { put(it.requestId, request) }
                            )
                        }
                    }
                    is FileTransferServerEvent.TransferStopped -> {
                        update { state ->
                            val sender = state.activeRequests[it.requestId]?.senderName ?: return@update state
                            val request = state.activeRequests[it.requestId]?.copy(stopReason = it.reason) ?: return@update state

                            state.copy(
                                senderRequestIdMap = state.senderRequestIdMap.toMutableMap().apply {
                                    val existingMap = this[sender] ?: emptyList()
                                    put(sender, existingMap.toMutableList().apply { remove(it.requestId) })
                                },
                                finishedRequests = state.finishedRequests.toMutableMap().apply { put(it.requestId, request) }
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun sendFile(device: Device, file: FileTransferRequest) {
        interactorScope.launch {
            fileTransferService.sendFile(file, device.ipAddress).collect {
                when (it) {
                    is FileTransferClientEvent.AwaitingAcceptance -> {
                        update { state ->
                            state.copy(pendingRequests = state.pendingRequests.toMutableMap().apply {
                                val fileTransfer = FileTransfer(
                                    requestId = it.requestId,
                                    fileName = file.fileName,
                                    senderName = file.senderName,
                                    bytesTotal = file.length,
                                    direction = FileTransferDirection.Sending,
                                )
                                put(it.requestId, fileTransfer)
                            })
                        }
                    }
                    is FileTransferClientEvent.TransferAccepted -> {
                        update { state ->
                            val pendingRequest = state.pendingRequests[it.requestId] ?: return@update state
                            state.copy(
                                pendingRequests = state.pendingRequests.toMutableMap().apply { remove(it.requestId) },
                                activeRequests = state.pendingRequests.toMutableMap().apply { put(it.requestId, pendingRequest) },
                            )
                        }
                    }
                    is FileTransferClientEvent.TransferProgress -> {
                        update { state ->
                            state.copy(activeRequests = state.activeRequests.toMutableMap().apply {
                                val request = this[it.requestId] ?: return@apply
                                put(it.requestId, request.copy(bytesTransferred = it.bytesSent))
                            })
                        }
                    }
                    is FileTransferClientEvent.TransferComplete -> {
                        update { state ->
                            val request = state.activeRequests[it.requestId] ?: return@update state

                            state.copy(
                                activeRequests = state.activeRequests.toMutableMap().apply { remove(it.requestId) },
                                finishedRequests = state.finishedRequests.toMutableMap().apply { put(it.requestId, request) }
                            )
                        }
                    }
                    is FileTransferClientEvent.TransferStopped -> {
                        val activeRequest = state.activeRequests[it.requestId]
                        val pendingRequest = state.pendingRequests[it.requestId]
                        val request = activeRequest ?: pendingRequest ?: return@collect

                        update { state ->
                            state.copy(
                                pendingRequests = if (pendingRequest != null) {
                                    state.pendingRequests.toMutableMap().apply { remove(it.requestId) }
                                } else {
                                    state.pendingRequests
                                },
                                activeRequests = if (activeRequest != null) {
                                    state.activeRequests.toMutableMap().apply { remove(it.requestId) }
                                } else {
                                    state.activeRequests
                                },
                                finishedRequests = state.finishedRequests.toMutableMap().apply {
                                    put(it.requestId, request.copy(stopReason = it.reason))
                                }
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

        fileTransferService.respondToTransferRequest(
            requestId = request.requestId,
            existingFileLength = when (mode) {
                FileWriteMode.Append -> request.bytesExisting
                else -> 0
            },
            response = response,
            sink = sink
        )

        update { state ->
            val pendingRequest = state.pendingRequests[request.requestId] ?: return@update state

            state.copy(
                pendingRequests = state.pendingRequests.toMutableMap().apply { remove(request.requestId) },
                activeRequests = if (response == FileTransferResponseType.Accepted) {
                    state.activeRequests.toMutableMap().apply { put(request.requestId, pendingRequest) }
                } else {
                    state.activeRequests
                },
                finishedRequests = if (response == FileTransferResponseType.Rejected) {
                    state.finishedRequests.toMutableMap().apply { put(request.requestId, pendingRequest) }
                } else {
                    state.finishedRequests
                },
                senderRequestIdMap = state.senderRequestIdMap.toMutableMap().apply {
                    val existingMap = this[request.senderName] ?: emptyList()
                    put(request.senderName, existingMap.toMutableList().apply { add(request.requestId) })
                }
            )
        }

        return Outcome.Ok(Unit)
    }
}