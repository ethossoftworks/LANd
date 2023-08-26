package com.ethossoftworks.land.common.ui.home

import com.ethossoftworks.land.common.interactor.filetransfer.FileTransfer
import com.ethossoftworks.land.common.interactor.filetransfer.FileTransferInteractor
import com.ethossoftworks.land.common.service.file.FileWriteMode
import com.ethossoftworks.land.common.service.filetransfer.FileTransferResponseType
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
        mode: FileWriteMode = FileWriteMode.Overwrite,
    ) {
        interactorScope.launch {
            fileTransferInteractor.respondToTransferRequest(request, response, mode)
        }
    }

    fun transferMessageQueueItemHandled(item: FileTransfer) {
        fileTransferInteractor.transferMessageHandled(item)
    }
}