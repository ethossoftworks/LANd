@file:Suppress("INLINE_FROM_HIGHER_PLATFORM") // https://youtrack.jetbrains.com/issue/KTIJ-20816/Bogus-error-Cannot-inline-bytecode-built-with-JVM-target-11-into-bytecode-that-is-being-built-with-JVM-target-1.8.

package com.ethossoftworks.land.common.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ethossoftworks.land.common.model.device.Device
import com.ethossoftworks.land.common.model.device.DevicePlatform
import com.ethossoftworks.land.common.resources.Resources
import com.ethossoftworks.land.common.ui.common.ImageButton
import com.ethossoftworks.land.common.ui.common.TextButton
import com.outsidesource.oskitcompose.canvas.rememberKmpPainterResource
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitcompose.modifier.outerShadow
import com.outsidesource.oskitcompose.systemui.KMPWindowInsets
import com.outsidesource.oskitcompose.systemui.StatusBarIconColorEffect
import com.outsidesource.oskitcompose.systemui.topInsets
import com.outsidesource.oskitcompose.systemui.verticalInsets
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current


@Composable
fun HomeScreen(
    interactor: HomeScreenViewInteractor = rememberInjectForRoute()
) {
    val state by interactor.collectAsState()
    val density = LocalDensity.current

    StatusBarIconColorEffect(useDarkIcons = true)

    BoxWithConstraints {
        val ringSpacing = maxHeight / 6f
        val sizeAnim = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            interactor.viewMounted()
        }

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
                            center = Offset(size.center.x, size.height - 72.dp.toPx()) // 72 = 16 (padding) + 16 (text) + 40 (.5 image height)
                        )
                    }
                }
                .windowInsetsPadding(KMPWindowInsets.verticalInsets)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
//                for (device in state.discoveredDevices.values) {
//                    DiscoveredDevice(device)
//                }
                DiscoveredDevice(Device("Test 1", DevicePlatform.iOS, ""))
                DiscoveredDevice(Device("Test 2", DevicePlatform.Android, ""))
                DiscoveredDevice(Device("Ryan's MacBook Pro", DevicePlatform.MacOS, ""))
                DiscoveredDevice(Device("Test 4", DevicePlatform.Windows, ""))
                DiscoveredDevice(Device("Test 5", DevicePlatform.Linux, ""))
                DiscoveredDevice(Device("Test 6", DevicePlatform.Unknown, ""))
            }

            if (!state.hasSaveFolder) {
                TextButton(
                    label = "Select a save folder",
                    onClick = interactor::onSelectSaveFolderClicked
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    modifier = Modifier
                        .size(80.dp),
                    painter = rememberKmpPainterResource(Resources.WifiTethering),
                    colorFilter = ColorFilter.tint(Color(0xFF155fd4)),
                    contentDescription = ""
                )
                TextButton(
                    label = "Visible as ${state.displayName}",
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun DiscoveredDevice(discoveredDevice: Device) {
    Column(
        modifier = Modifier.width(80.dp),
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
                .background(Color(0xFFEEEEEE))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFEEEEEE),
//                    Color(0xFFd5dae3),
                            Color(0x22155fd4)
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
                contentDescription = null
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