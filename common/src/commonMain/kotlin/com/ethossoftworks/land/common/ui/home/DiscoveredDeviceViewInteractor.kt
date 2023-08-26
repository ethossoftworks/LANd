package com.ethossoftworks.land.common.ui.home

import com.ethossoftworks.land.common.interactor.filetransfer.FileTransfer
import com.ethossoftworks.land.common.interactor.filetransfer.FileTransferDirection
import com.ethossoftworks.land.common.interactor.filetransfer.FileTransferInteractor
import com.ethossoftworks.land.common.interactor.filetransfer.FileTransferStatus
import com.ethossoftworks.land.common.interactor.preferences.AppPreferencesInteractor
import com.ethossoftworks.land.common.model.device.Device
import com.ethossoftworks.land.common.service.file.IFileHandler
import com.ethossoftworks.land.common.service.filetransfer.CancellationCommand
import com.ethossoftworks.land.common.service.filetransfer.FileTransferRequest
import com.outsidesource.oskitcompose.modifier.KMPDragData
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.Outcome
import io.ktor.http.*
import kotlinx.coroutines.launch

data class DiscoveredDeviceState(
    val isWaiting: Boolean = false,
    val isSending: Boolean = false,
    val isTransferring: Boolean = false,
    val totalProgress: Float = 0f,
    val receivingProgress: Float = 0f,
    val sendingProgress: Float = 0f,
    val transfers: List<FileTransfer> = emptyList(),
)

class DiscoveredDeviceViewInteractor(
    private val deviceName: String,
    private val fileTransferInteractor: FileTransferInteractor,
    private val fileHandler: IFileHandler,
    private val appPreferencesInteractor: AppPreferencesInteractor,
): Interactor<DiscoveredDeviceState>(
    initialState = DiscoveredDeviceState(),
    dependencies = listOf(fileTransferInteractor)
) {
    override fun computed(state: DiscoveredDeviceState): DiscoveredDeviceState {
        val transferIds = fileTransferInteractor.state.deviceToTransferIdMap[deviceName] ?: emptyList()
        var isWaiting = false
        var sendingBytesTotal = 0L
        var sendingBytesTransferred = 0L
        var receivingBytesTotal = 0L
        var receivingBytesTransferred = 0L
        var isTransferring = false

        val transfers = transferIds.mapNotNull { id ->
            val transfer = fileTransferInteractor.state.activeTransfers[id] ?: return@mapNotNull null

            if (transfer.direction == FileTransferDirection.Sending) {
                sendingBytesTotal += transfer.bytesTotal
                sendingBytesTransferred += transfer.bytesTransferred
            } else {
                receivingBytesTotal += transfer.bytesTotal
                receivingBytesTransferred += transfer.bytesTransferred
            }

            isWaiting = isWaiting ||
                    (transfer.status == FileTransferStatus.AwaitingAcceptance &&
                            transfer.direction == FileTransferDirection.Sending)
            isTransferring = isTransferring || transfer.status == FileTransferStatus.Progress

            transfer
        }

        val totalBytes = receivingBytesTotal + sendingBytesTotal
        val totalTransferred = receivingBytesTransferred + sendingBytesTransferred

        return state.copy(
            isWaiting = isWaiting,
            isTransferring = isTransferring,
            totalProgress = if (totalBytes > 0) (totalTransferred.toFloat() / totalBytes.toFloat()) else 0f,
            receivingProgress = if (receivingBytesTotal > 0) (receivingBytesTransferred.toFloat() / receivingBytesTotal.toFloat()) else 0f,
            sendingProgress = if (sendingBytesTotal > 0) (sendingBytesTransferred.toFloat() / sendingBytesTotal.toFloat()) else 0f,
            transfers = transfers,
        )
    }

    fun onFilesDropped(device: Device, data: KMPDragData.FilesList) {
        val files = data.readFiles().map { it.removePrefix("file:").decodeURLPart() }

        files.forEach {file ->
            interactorScope.launch {
                val metadataOutcome = fileHandler.readFileMetadata(file)
                if (metadataOutcome !is Outcome.Ok) return@launch
                if (metadataOutcome.value.isDirectory) return@launch

                val sourceOutcome = fileHandler.openFileToRead(file)
                if (sourceOutcome !is Outcome.Ok) return@launch

                val fileTransfer = FileTransferRequest(
                    senderName = appPreferencesInteractor.state.displayName,
                    fileName = metadataOutcome.value.name,
                    length = metadataOutcome.value.length,
                    source = sourceOutcome.value,
                )

                fileTransferInteractor.sendFile(device, fileTransfer)
            }
        }
    }

    fun onStopTransferClicked(transferId: Short) {
        interactorScope.launch {
            fileTransferInteractor.cancelTransfer(transferId, CancellationCommand.Stop)
        }
    }

    fun onStopAndDeleteTransferClicked(transferId: Short) {
        interactorScope.launch {
            fileTransferInteractor.cancelTransfer(transferId, CancellationCommand.Delete)
        }
    }
}