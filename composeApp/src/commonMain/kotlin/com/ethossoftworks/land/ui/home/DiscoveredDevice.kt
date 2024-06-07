@file:OptIn(ExperimentalResourceApi::class)

package com.ethossoftworks.land.ui.home

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ethossoftworks.land.entity.Device
import com.ethossoftworks.land.entity.DevicePlatform
import com.ethossoftworks.land.interactor.filetransfer.FileTransfer
import com.ethossoftworks.land.interactor.filetransfer.FileTransferDirection
import com.ethossoftworks.land.ui.common.theme.AppTheme
import com.outsidesource.oskitcompose.geometry.PopupShape
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.layout.spaceBetweenPadded
import com.outsidesource.oskitcompose.lib.rememberInject
import com.outsidesource.oskitcompose.modifier.KMPDragData
import com.outsidesource.oskitcompose.modifier.kmpOnExternalDrag
import com.outsidesource.oskitcompose.modifier.kmpPointerMoveFilter
import com.outsidesource.oskitcompose.modifier.outerShadow
import com.outsidesource.oskitcompose.popup.Popover
import com.outsidesource.oskitcompose.popup.PopoverAnchors
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current
import land.composeapp.generated.resources.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt


@OptIn(ExperimentalFoundationApi::class, ExperimentalResourceApi::class)
@Composable
fun DiscoveredDevice(
    device: Device,
    onClick: () -> Unit,
    interactor: DiscoveredDeviceViewInteractor = rememberInject(device.name) { parametersOf(device.name) },
) {
    val state = interactor.collectAsState()
    var isDropping by remember { mutableStateOf(false) }
    val dropAnim by animateFloatAsState(if (isDropping) 1.2f else 1f)
    val colors = AppTheme.colors

    Column(
        modifier = Modifier
            .width(90.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val progressTransition = updateTransition(state.totalProgress)
        val progressAnim by progressTransition.animateFloat { it }
        val progressArcSpacing = 4.dp
        val isHovering = remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(dropAnim)
                .kmpPointerMoveFilter(
                    onEnter = {
                        if (state.transfers.isEmpty()) return@kmpPointerMoveFilter false
                        isHovering.value = true
                        false
                    },
                    onExit = {
                        isHovering.value = false
                        false
                    }
                )
                .kmpOnExternalDrag(
                    onDragStart = { isDropping = true },
                    onDragExit = { isDropping = false },
                    onDrop = {
                        isDropping = false
                        val data = it.dragData
                        if (data is KMPDragData.FilesList) interactor.onFilesDropped(device, data)
                    }
                )
                .drawBehind {
                    if (progressTransition.targetState == 0f) return@drawBehind

                    drawArc(
                        color = colors.primary,
                        style = Stroke(width = 2.5.dp.toPx()),
                        useCenter = false,
                        topLeft = Offset(-(progressArcSpacing).toPx(), -(progressArcSpacing).toPx()),
                        size = Size(size.width + (progressArcSpacing * 2).toPx(), size.height + (progressArcSpacing * 2).toPx()),
                        startAngle = -90f,
                        sweepAngle = 360f * progressAnim,
                    )
                }
                .outerShadow(
                    blur = 2.dp,
                    color = Color.Black.copy(alpha = .25f),
                    shape = CircleShape,
                    offset = DpOffset(0.dp, 2.dp)
                )
                .clip(CircleShape)
                .combinedClickable(
                    onLongClick = {
                        if (Platform.current.isDesktop) return@combinedClickable
                        if (state.transfers.isEmpty()) return@combinedClickable
                        isHovering.value = true
                    },
                    onClick = { onClick() }
                )
                .background(Color(0xFFEEEEEE))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xCC155fd4),
                            Color(0xCC6198ef),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            StopTransferPopover(
                isDiscoveredDeviceHovering = isHovering,
                transfers = state.transfers,
                onStopClicked = interactor::onStopTransferClicked,
                onStopAndDeleteClicked = interactor::onStopAndDeleteTransferClicked,
            )
            Image(
                modifier = Modifier.width(48.dp),
                painter = painterResource(
                    when (device.platform) {
                        DevicePlatform.iOS -> Res.drawable.device_mobile_ios
                        DevicePlatform.Android -> Res.drawable.device_mobile_android
                        DevicePlatform.MacOS -> Res.drawable.device_desktop_macos
                        DevicePlatform.Windows -> Res.drawable.device_desktop_windows
                        DevicePlatform.Linux -> Res.drawable.device_desktop_linux
                        DevicePlatform.Unknown -> Res.drawable.device_unknown
                    }
                ),
                contentDescription = null,
            )
        }
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = device.name,
            fontSize = 12.sp,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 1.2.em,
        )
        Text(
            text = when {
                state.isTransferring && state.totalProgress == 0f && state.receivingProgress == 0f -> "Transferring..."
                state.totalProgress > 0f || state.receivingProgress > 0f -> buildString {
                    val hasSend = state.sendingProgress > 0f
                    val hasReceive = state.receivingProgress > 0f
                    if (hasSend) append("Sending ${(state.sendingProgress * 100).roundToInt()}%")
                    if (hasSend && hasReceive) append("\n")
                    if (hasReceive) append("Receiving ${(state.receivingProgress * 100).roundToInt()}%")
                }
                state.isWaiting -> "Waiting..."
                state.isConnecting -> "Connecting..."
                else -> ""
            },
            style = AppTheme.typography.deviceTransferStatus,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            lineHeight = 1.2.em,
        )
    }
}

@Composable
private fun StopTransferPopover(
    transfers: List<FileTransfer>,
    isDiscoveredDeviceHovering: MutableState<Boolean>,
    onStopClicked: (Short) -> Unit,
    onStopAndDeleteClicked: (Short) -> Unit,
) {
    var isPopoverHovering by remember { mutableStateOf(false) }
    val popoverCorners = remember { PopupShape(caretPointHeight = 12.dp) }

    Popover(
        isVisible = isDiscoveredDeviceHovering.value || isPopoverHovering,
        anchors = PopoverAnchors.ExternalTopAlignCenter,
        focusable = Platform.current.isMobile, // Setting this to false on Desktop prevents the popover from breaking hover functionality
        onDismissRequest = {
            isDiscoveredDeviceHovering.value = false
            isPopoverHovering = false
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .width(200.dp)
                .kmpPointerMoveFilter(
                    onEnter = {
                        isPopoverHovering = true
                        false
                    },
                    onExit = {
                        isPopoverHovering = false
                        false
                    }
                )
                .padding(bottom = 8.dp + 12.dp)
                .outerShadow(
                    blur = 4.dp,
                    color = Color.Black.copy(alpha = .5f),
                    offset = DpOffset(0.dp, 2.dp),
                    shape = popoverCorners,
                )
                .clip(popoverCorners)
                .background(AppTheme.colors.tertiary, popoverCorners),
        ) {
            items(transfers, key = { it.transferId }) {
                MenuOption(
                    label = it.fileName,
                    direction = it.direction,
                    onStopClick = {
                        isPopoverHovering = false
                        isDiscoveredDeviceHovering.value = false
                        onStopClicked(it.transferId)
                    },
                    onDeleteClick = {
                        isPopoverHovering = false
                        isDiscoveredDeviceHovering.value = false
                        onStopAndDeleteClicked(it.transferId)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun MenuOption(
    label: String,
    direction: FileTransferDirection,
    onStopClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp),
        horizontalArrangement = Arrangement.spaceBetweenPadded(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier.size(16.dp),
            colorFilter = ColorFilter.tint(Color.White),
            painter = painterResource(when (direction) {
                FileTransferDirection.Receiving -> Res.drawable.download
                FileTransferDirection.Sending -> Res.drawable.upload
            }),
            contentDescription = when (direction) {
                FileTransferDirection.Receiving -> "Receiving"
                FileTransferDirection.Sending -> "Sending"
            }
        )
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = AppTheme.typography.transferMessageText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Image(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onStopClick),
            painter = painterResource(Res.drawable.stop),
            contentDescription = "Stop",
            colorFilter = ColorFilter.tint(Color.White)
        )
        Image(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onDeleteClick),
            painter = painterResource(Res.drawable.delete),
            contentDescription = "Delete",
            colorFilter = ColorFilter.tint(Color.White)
        )
    }
}