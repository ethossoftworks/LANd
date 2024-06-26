package com.ethossoftworks.land.ui.home

import com.ethossoftworks.land.interactor.filetransfer.FileTransfer
import com.ethossoftworks.land.interactor.filetransfer.FileTransferInteractor
import com.ethossoftworks.land.service.filetransfer.FileTransferResponseType
import com.outsidesource.oskitkmp.file.KMPFileWriteMode
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.coroutines.launch

data class TransferMessageState(
    val transfer: FileTransfer? = null
)

class TransferMessageViewInteractor(
    private val fileTransferInteractor: FileTransferInteractor,
) : Interactor<TransferMessageState>(
    initialState = TransferMessageState(),
    dependencies = listOf(fileTransferInteractor),
) {

    override fun computed(state: TransferMessageState): TransferMessageState {
        return state.copy(
            transfer = fileTransferInteractor.state.transferMessageQueue.firstOrNull()
        )
    }

    fun respondToRequest(
        request: FileTransfer,
        response: FileTransferResponseType,
        mode: KMPFileWriteMode = KMPFileWriteMode.Overwrite,
    ) {
        interactorScope.launch {
            fileTransferInteractor.respondToTransferRequest(request, response, mode)
        }
    }

    fun transferMessageQueueItemHandled(item: FileTransfer) {
        fileTransferInteractor.transferMessageHandled(item)
    }
}