package com.ethossoftworks.land.common.ui.home

import com.ethossoftworks.land.common.interactor.filetransfer.FileTransferDirection
import com.ethossoftworks.land.common.interactor.filetransfer.FileTransferInteractor
import com.ethossoftworks.land.common.interactor.filetransfer.FileTransferStatus
import com.outsidesource.oskitkmp.interactor.Interactor

data class DiscoveredDeviceState(
    val isWaiting: Boolean = false,
    val receivingProgress: Float = 0f,
    val sendingProgress: Float = 0f,
)

class DiscoveredDeviceViewInteractor(
    private val deviceName: String,
    private val fileTransferInteractor: FileTransferInteractor,
): Interactor<DiscoveredDeviceState>(
    initialState = DiscoveredDeviceState(),
    dependencies = listOf(fileTransferInteractor)
) {
    override fun computed(state: DiscoveredDeviceState): DiscoveredDeviceState {
        val transferIds = fileTransferInteractor.state.deviceNameRequestIdMap[deviceName] ?: emptyList()
        var isWaiting = false
        var sendingBytesTotal = 0L
        var sendingBytesTransferred = 0L
        var receivingBytesTotal = 0L
        var receivingBytesTransferred = 0L

        transferIds.forEach { id ->
            val transfer = fileTransferInteractor.state.activeRequests[id] ?: return@forEach

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
        }

        return state.copy(
            isWaiting = isWaiting,
            receivingProgress = if (receivingBytesTotal > 0) (receivingBytesTransferred.toFloat() / receivingBytesTotal.toFloat()) else 0f,
            sendingProgress = if (sendingBytesTotal > 0) (sendingBytesTransferred.toFloat() / sendingBytesTotal.toFloat()) else 0f,
        )
    }
}