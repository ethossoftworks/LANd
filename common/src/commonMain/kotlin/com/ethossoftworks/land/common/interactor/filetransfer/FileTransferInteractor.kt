package com.ethossoftworks.land.common.interactor.filetransfer

import com.ethossoftworks.land.common.interactor.preferences.AppPreferencesInteractor
import com.ethossoftworks.land.common.model.device.Device
import com.ethossoftworks.land.common.service.file.FileWriteMode
import com.ethossoftworks.land.common.service.file.IFileHandler
import com.ethossoftworks.land.common.service.filetransfer.*
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.launch

data class FileTransferState(
    val isServerRunning: Boolean = false,
    val pendingRequests: Map<Short, FileTransferServerEvent.TransferRequested> = emptyMap(),
    val activeReceives: Map<String, Map<Short, FileTransferServerEvent.TransferProgress>> = emptyMap(),
    val activeSends: Map<String, Map<Short, FileTransferServerEvent.TransferProgress>> = emptyMap(),
    val sendErrors: Map<Short, FileTransferClientEvent.TransferStopped> = emptyMap(),
    val receiveErrors: Map<Short, FileTransferClientEvent.TransferStopped> = emptyMap(),
)

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
                        update { state ->
                            state.copy(pendingRequests = state.pendingRequests.toMutableMap().apply {
                                put(it.requestId, it)
                            })
                        }
                    }
                    is FileTransferServerEvent.TransferProgress -> {
                        update { state ->
                            state.copy(activeReceives = state.activeReceives.toMutableMap().apply {
//                                val existing = state.activeReceives[it.]
                                // TODO: Need to map existing request information to progress
                            })
                        }
                    }
                    is FileTransferServerEvent.TransferComplete -> {

                    }
                    is FileTransferServerEvent.TransferStopped -> {

                    }
                }
            }
        }
    }

    suspend fun sendFile(device: Device, file: FileTransfer) {
        interactorScope.launch {
            fileTransferService.sendFile(file, device.ipAddress).collect {
                when (it) {
                    is FileTransferClientEvent.AwaitingAcceptance -> {

                    }
                    is FileTransferClientEvent.TransferProgress -> {

                    }
                    is FileTransferClientEvent.TransferComplete -> {

                    }
                    is FileTransferClientEvent.TransferStopped -> {

                    }
                }
            }
        }
    }

    suspend fun respondToRequest(
        request: FileTransferServerEvent.TransferRequested,
        response: FileTransferResponseType,
        mode: FileWriteMode
    ): Outcome<Unit, Any> {
        // TODO: Implement better early return errors
        val saveFolder = appPreferencesInteractor.state.saveFolder ?: return Outcome.Error(Unit)
        val metadataOutcome = if (mode == FileWriteMode.Append) {
            fileHandler.readFileMetadata(saveFolder, request.fileName)
        } else {
            Outcome.Error(Unit) // Ignore reading metadata if the mode is FileWriteMode.Overwrite
        }

        val sink = fileHandler.openFileToWrite(saveFolder, request.fileName, mode)
        if (sink !is Outcome.Ok) return Outcome.Error(Unit)

        fileTransferService.respondToTransferRequest(
            requestId = request.requestId,
            existingFileLength = when (metadataOutcome) {
                is Outcome.Ok -> metadataOutcome.value.length
                else -> 0
            },
            response = response,
            sink = sink.value
        )

        update { state ->
            state.copy(
                pendingRequests = state.pendingRequests.toMutableMap().apply { remove(request.requestId) }
            )
        }

        return Outcome.Ok(Unit)
    }
}