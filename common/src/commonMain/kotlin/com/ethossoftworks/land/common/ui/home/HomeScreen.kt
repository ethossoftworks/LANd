@file:Suppress("INLINE_FROM_HIGHER_PLATFORM") // https://youtrack.jetbrains.com/issue/KTIJ-20816/Bogus-error-Cannot-inline-bytecode-built-with-JVM-target-11-into-bytecode-that-is-being-built-with-JVM-target-1.8.

package com.ethossoftworks.land.common.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotContextElement
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import com.ethossoftworks.land.common.model.device.Device
import com.ethossoftworks.land.common.model.device.DevicePlatform
import com.ethossoftworks.land.common.resources.Resources
import com.ethossoftworks.land.common.ui.common.ImageButton
import com.ethossoftworks.land.common.ui.common.TextButton
import com.outsidesource.oskitcompose.canvas.rememberKmpPainterResource
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.layout.WrappableRow
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitcompose.modifier.outerShadow
import com.outsidesource.oskitcompose.systemui.KMPWindowInsets
import com.outsidesource.oskitcompose.systemui.StatusBarIconColorEffect
import com.outsidesource.oskitcompose.systemui.topInsets
import com.outsidesource.oskitcompose.systemui.verticalInsets
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    interactor: HomeScreenViewInteractor = rememberInjectForRoute()
) {
    val state by interactor.collectAsState()

    LaunchedEffect(Unit) {
        interactor.viewMounted()
    }

    StatusBarIconColorEffect(useDarkIcons = true)

    BoxWithConstraints {
        val ringSpacing = maxHeight / 6f

        ImageButton(
            modifier = Modifier
                .then(if (Platform.current == Platform.Android) {
                    Modifier.windowInsetsPadding(KMPWindowInsets.topInsets)
                } else {
                    Modifier
                })
                .align(Alignment.TopEnd)
                .zIndex(1f)
                .padding(top = 16.dp, end = 16.dp),
            resource = Resources.Settings,
            onClick = {},
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(KMPWindowInsets.verticalInsets)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                modifier = Modifier
                    .weight(1f)
                    .zIndex(1f),
                targetState = state.discoveredDevices,
                transitionSpec = { EnterTransition.None with ExitTransition.None },
            ) { devices ->
                WrappableRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    verticalSpacing = 16.dp,
                ) {
                    for (device in devices.values) {
                        Box(
                            modifier = Modifier.animateEnterExit(
                                enter = fadeIn() + scaleIn(initialScale = .85f),
                                exit = fadeOut() + scaleOut(targetScale = .85f),
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            DiscoveredDevice(device, onClick = { interactor.onDeviceClicked(device) })
                        }
                    }
                }
            }

            if (!state.hasSaveFolder) {
                TextButton(
                    label = "Select a save folder",
                    onClick = interactor::onSelectSaveFolderClicked
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RadiatingLogo(ringSpacing)
                TextButton(
                    label = "Visible as ${state.displayName}",
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun RadiatingLogo(ringSpacing: Dp) {
    val density = LocalDensity.current
    val sizeAnim = remember { Animatable(0f) }

    LaunchedEffect(ringSpacing) {
        sizeAnim.snapTo(0f)
        sizeAnim.animateTo(
            targetValue = with(density) { ringSpacing.toPx() },
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2_000,
                    easing = LinearEasing
                )
            ),
        )
    }

    Image(
        modifier = Modifier
            .size(80.dp)
            .drawBehind {
                for (i in 0..6) {
                    drawCircle(
                        color = Color(0xFFEEEEEE).copy(
                            alpha = when (i) {
                                6 -> (1f - sizeAnim.value / ringSpacing.toPx()).coerceIn(0f..1f)
                                0 -> (sizeAnim.value / ringSpacing.toPx()).coerceIn(0f..1f)
                                else -> 1f
                            }
                        ),
                        radius = 40.dp.toPx() + (ringSpacing.toPx() * i) + sizeAnim.value,
                        style = Stroke(1.dp.toPx()),
                        center = Offset(size.center.x, size.center.y)
                    )
                }
            },
        painter = rememberKmpPainterResource(Resources.WifiTethering),
        colorFilter = ColorFilter.tint(Color(0xFF155fd4)),
        contentDescription = ""
    )
}

@Composable
private fun DiscoveredDevice(
    discoveredDevice: Device,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .outerShadow(
                    blur = 2.dp,
                    color = Color.Black.copy(alpha = .25f),
                    shape = CircleShape,
                    offset = DpOffset(0.dp, 2.dp)
                )
                .clip(CircleShape)
                .clickable { onClick() }
                .background(Color(0xFFEEEEEE))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
//                            Color(0xFFEEEEEE),
//                            Color(0x22155fd4),
                            Color(0xCC155fd4),
                            Color(0xCC6198ef),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                modifier = Modifier.width(48.dp),
                painter = rememberKmpPainterResource(
                    when (discoveredDevice.platform) {
                        DevicePlatform.iOS -> Resources.DeviceMobileIOS
                        DevicePlatform.Android -> Resources.DeviceMobileAndroid
                        DevicePlatform.MacOS -> Resources.DeviceDesktopMacOS
                        DevicePlatform.Windows -> Resources.DeviceDesktopWindows
                        DevicePlatform.Linux -> Resources.DeviceDesktopLinux
                        DevicePlatform.Unknown -> Resources.DeviceUnknown
                    }
                ),
                contentDescription = null,
            )
        }
        Text(
            text = discoveredDevice.name,
            fontSize = 12.sp,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 1.2.em,
        )
    }
}