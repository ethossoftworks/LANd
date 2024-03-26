package com.ethossoftworks.land.ui.home

import com.ethossoftworks.land.interactor.filetransfer.FileTransfer
import com.ethossoftworks.land.interactor.filetransfer.FileTransferDirection
import com.ethossoftworks.land.interactor.filetransfer.FileTransferInteractor
import com.ethossoftworks.land.interactor.filetransfer.FileTransferStatus
import com.ethossoftworks.land.interactor.preferences.AppPreferencesInteractor
import com.ethossoftworks.land.entity.Device
import com.ethossoftworks.land.service.filetransfer.CancellationCommand
import com.ethossoftworks.land.service.filetransfer.FileTransferRequest
import com.outsidesource.oskitcompose.modifier.KMPDragData
import com.outsidesource.oskitkmp.file.IKMPFileHandler
import com.outsidesource.oskitkmp.file.source
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
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
    private val fileHandler: IKMPFileHandler,
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

        files.forEach { file ->
            interactorScope.launch {
                val fileRef = fileHandler.resolveRefFromPath(file).unwrapOrReturn { return@launch }
                val metadata = fileHandler.readMetadata(fileRef).unwrapOrReturn { return@launch }
                if (fileRef.isDirectory) return@launch
                val source = fileRef.source().unwrapOrReturn { return@launch }

                val fileTransfer = FileTransferRequest(
                    senderName = appPreferencesInteractor.state.displayName,
                    fileName = fileRef.name,
                    length = metadata.size,
                    source = source,
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