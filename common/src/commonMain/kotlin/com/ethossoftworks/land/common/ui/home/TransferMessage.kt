import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ethossoftworks.land.common.interactor.filetransfer.FileTransfer
import com.ethossoftworks.land.common.interactor.filetransfer.FileTransferStatus
import com.ethossoftworks.land.common.service.file.FileWriteMode
import com.ethossoftworks.land.common.service.filetransfer.FileTransferResponseType
import com.ethossoftworks.land.common.service.filetransfer.FileTransferStopReason
import com.ethossoftworks.land.common.ui.common.theme.AppTheme
import com.ethossoftworks.land.common.ui.home.HomeScreenViewInteractor
import com.outsidesource.oskitcompose.animation.TransitionAnimatedContent
import com.outsidesource.oskitcompose.lib.ValRef
import com.outsidesource.oskitcompose.modifier.outerShadow
import com.outsidesource.oskitcompose.systemui.KMPWindowInsets
import com.outsidesource.oskitcompose.systemui.bottomInsets
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current

@Composable
fun BoxScope.TransferMessage(
    transfer: FileTransfer?,
    interactorRef: ValRef<HomeScreenViewInteractor>,
) {
    val interactor = interactorRef.value

    TransitionAnimatedContent(
        targetState = transfer,
        modifier = Modifier.align(if (Platform.current.isDesktop) Alignment.BottomEnd else Alignment.BottomCenter),
    ) { fileTransfer, transition ->
        if (fileTransfer == null) return@TransitionAnimatedContent
        val alphaAnim by transition.animateFloat(transitionSpec = { tween(250) }) { if (it == fileTransfer) 1f else 0f }
        val positionAnim by transition.animateFloat(transitionSpec = { tween(250) }) { if (it == fileTransfer) 0f else 10f }

        Column(
            modifier = Modifier
                .graphicsLayer {
                    alpha = alphaAnim
                    translationY = positionAnim
                }
                .width(350.dp)
                .padding(AppTheme.dimensions.screenHPadding, AppTheme.dimensions.screenVPadding)
                .windowInsetsPadding(KMPWindowInsets.bottomInsets)
                .outerShadow(
                    blur = 8.dp,
                    shape = RoundedCornerShape(8.dp),
                    offset = DpOffset(0.dp, 2.dp),
                    color = Color.Black.copy(alpha = .2f)
                )
                .clip(RoundedCornerShape(8.dp))
//                .border(width = 1.dp, color = Color(0xFFD0D0D0), RoundedCornerShape(8.dp))
//                .background(Color(0xFFF6F6F6))
//                .border(width = 1.dp, color = Color(0xFF393a41), RoundedCornerShape(8.dp))
                .background(AppTheme.colors.tertiary)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            when (fileTransfer.status) {
                FileTransferStatus.AwaitingAcceptance -> {
                    TransferMessageText(
                        text = "\"${fileTransfer.deviceName}\" wants to send you \"${fileTransfer.fileName}\" (${fileTransfer.sizeString()})."
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (fileTransfer.bytesExisting > 0 && fileTransfer.bytesExisting != fileTransfer.bytesTotal) {
                            TransferMessageButton(
                                label = "Reject",
                                isPrimary = false,
                                onClick = {
                                    interactor.respondToRequest(fileTransfer, FileTransferResponseType.Rejected)
                                }
                            )
                            TransferMessageButton(
                                label = "Overwrite",
                                onClick = {
                                    interactor.respondToRequest(
                                        fileTransfer,
                                        FileTransferResponseType.Accepted,
                                        FileWriteMode.Overwrite,
                                    )
                                }
                            )
                            TransferMessageButton(
                                label = "Continue",
                                onClick = {
                                    interactor.respondToRequest(
                                        fileTransfer,
                                        FileTransferResponseType.Accepted,
                                        FileWriteMode.Append,
                                    )
                                }
                            )
                        } else {
                            TransferMessageButton(
                                label = "Reject",
                                isPrimary = false,
                                onClick = {
                                    interactor.respondToRequest(fileTransfer, FileTransferResponseType.Rejected)
                                }
                            )
                            TransferMessageButton(
                                label = "Accept",
                                onClick = {
                                    interactor.respondToRequest(
                                        fileTransfer,
                                        FileTransferResponseType.Accepted,
                                        FileWriteMode.Overwrite
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
                            FileTransferStopReason.SocketClosed -> "Transfer stopped due to a connection failure."
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