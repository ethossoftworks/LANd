package com.ethossoftworks.land.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ethossoftworks.land.interactor.filetransfer.FileTransferDirection
import com.ethossoftworks.land.interactor.filetransfer.FileTransferStatus
import com.ethossoftworks.land.service.filetransfer.FileTransferResponseType
import com.ethossoftworks.land.service.filetransfer.FileTransferStopReason
import com.ethossoftworks.land.ui.common.InfoMessage
import com.ethossoftworks.land.ui.common.theme.AppTheme
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitkmp.file.KMPFileWriteMode
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current

@Composable
fun BoxScope.TransferMessage(
    interactor: TransferMessageViewInteractor = rememberInjectForRoute()
) {
    val state = interactor.collectAsState()

    InfoMessage(
        targetState = state.transfer,
        modifier = Modifier.align(if (Platform.current.isDesktop) Alignment.BottomEnd else Alignment.BottomCenter),
    ) { fileTransfer ->
        if (fileTransfer == null) return@InfoMessage

        when (fileTransfer.status) {
            FileTransferStatus.AwaitingAcceptance -> {
                TransferMessageText(
                    text = "\"${fileTransfer.deviceName}\" wants to send you \"${fileTransfer.fileName}\" (${fileTransfer.sizeString()})."
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransferMessageButton(
                        label = "Reject",
                        isPrimary = false,
                        onClick = {
                            interactor.respondToRequest(fileTransfer, FileTransferResponseType.Rejected)
                        }
                    )
                    if (fileTransfer.bytesExisting > 0) {
                        TransferMessageButton(
                            label = "Overwrite",
                            onClick = {
                                interactor.respondToRequest(
                                    fileTransfer,
                                    FileTransferResponseType.Accepted,
                                    KMPFileWriteMode.Overwrite,
                                )
                            }
                        )
                        if (fileTransfer.bytesExisting != fileTransfer.bytesTotal) {
                            TransferMessageButton(
                                label = "Continue",
                                onClick = {
                                    interactor.respondToRequest(
                                        fileTransfer,
                                        FileTransferResponseType.Accepted,
                                        KMPFileWriteMode.Append,
                                    )
                                }
                            )
                        }
                    } else {
                        TransferMessageButton(
                            label = "Accept",
                            onClick = {
                                interactor.respondToRequest(
                                    fileTransfer,
                                    FileTransferResponseType.Accepted,
                                    KMPFileWriteMode.Overwrite
                                )
                            }
                        )
                    }
                }
            }

            FileTransferStatus.Stopped -> {
                TransferMessageText(
                    text = when (fileTransfer.stopReason) {
                        FileTransferStopReason.UnableToOpenFile -> "Transfer stopped. Unable to create file."
                        FileTransferStopReason.SocketClosed -> "Transfer stopped. Connection closed unexpectedly."
                        FileTransferStopReason.UnknownProtocol,
                        FileTransferStopReason.UnknownEncryptionMethod -> "The selected device is using a newer version of LANd"
                        is FileTransferStopReason.UserCancelled -> when (fileTransfer.direction) {
                            FileTransferDirection.Receiving -> "\"${fileTransfer.deviceName}\" cancelled sending \"${fileTransfer.fileName}\""
                            FileTransferDirection.Sending -> "\"${fileTransfer.deviceName}\" cancelled receiving \"${fileTransfer.fileName}\""
                        }
                        else -> "Transfer stopped for an unknown reason"
                    }
                )
                TransferMessageButton(
                    label = "Ok",
                    onClick = { interactor.transferMessageQueueItemHandled(fileTransfer) },
                )
            }

            FileTransferStatus.Rejected -> {
                TransferMessageText("\"${fileTransfer.deviceName}\" rejected your request to send \"${fileTransfer.fileName}\"")
                TransferMessageButton(
                    label = "Ok",
                    onClick = { interactor.transferMessageQueueItemHandled(fileTransfer) },
                )
            }

            else -> {}
        }
    }
}

@Composable
private fun TransferMessageText(
    text: String
) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        style = AppTheme.typography.transferMessageText,
        overflow = TextOverflow.Ellipsis,
        maxLines = 3,
    )
}

@Composable
private fun TransferMessageButton(
    label: String,
    isPrimary: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .background(Color.Black.copy(alpha = .15f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        style = if (isPrimary) AppTheme.typography.transferMessageButtonPrimary else AppTheme.typography.transferMessageButtonSecondary,
        text = label,
    )
}