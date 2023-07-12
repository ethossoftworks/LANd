package com.ethossoftworks.land.common.interactor.filetransfer

import com.ethossoftworks.land.common.model.device.Device
import com.ethossoftworks.land.common.service.filetransfer.FileTransfer
import com.ethossoftworks.land.common.service.filetransfer.FileTransferServerEvent
import com.ethossoftworks.land.common.service.filetransfer.IFileTransferService
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath

data class FileTransferState(
    val lastServerEvent: FileTransferServerEvent = FileTransferServerEvent.Idle,
)

class FileTransferInteractor(
    private val fileTransferService: IFileTransferService,
): Interactor<FileTransferState>(
    initialState = FileTransferState(),
) {

    suspend fun startServer() {
        interactorScope.launch {
            fileTransferService.startServer().collect {
                println(it)
                update { state -> state.copy(lastServerEvent = it) }
            }
        }
    }

    suspend fun sendFile(device: Device, file: FileTransfer) {
        interactorScope.launch {
            fileTransferService.sendFile(
                file,
                device.ipAddress
            ).collect {
                println(it)
            }
        }
    }
}