@file:Suppress("INLINE_FROM_HIGHER_PLATFORM") // https://youtrack.jetbrains.com/issue/KTIJ-20816/Bogus-error-Cannot-inline-bytecode-built-with-JVM-target-11-into-bytecode-that-is-being-built-with-JVM-target-1.8.

package com.ethossoftworks.land.common.ui.home

import TransferMessage
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ethossoftworks.land.common.resources.Resources
import com.ethossoftworks.land.common.ui.common.AppTextField
import com.ethossoftworks.land.common.ui.common.ImageButton
import com.ethossoftworks.land.common.ui.common.PrimaryButton
import com.ethossoftworks.land.common.ui.common.SecondaryButton
import com.ethossoftworks.land.common.ui.common.form.FormField
import com.ethossoftworks.land.common.ui.common.form.FormSection
import com.ethossoftworks.land.common.ui.common.theme.AppTheme
import com.outsidesource.oskitcompose.canvas.rememberKmpPainterResource
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.layout.WrappableRow
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitcompose.lib.rememberValRef
import com.outsidesource.oskitcompose.popup.Modal
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

        Row(
            modifier = Modifier
                .then(if (Platform.current == Platform.Android) {
                    Modifier.windowInsetsPadding(KMPWindowInsets.topInsets)
                } else {
                    Modifier
                })
                .align(Alignment.TopEnd)
                .zIndex(1f)
                .padding(top = 16.dp, end = 16.dp),
        ) {
            ImageButton(
                resource = Resources.Add,
                tint = AppTheme.colors.homeScreenButtonTint,
                onClick = interactor::onAddButtonClicked,
            )
            ImageButton(
                resource = Resources.Info,
                tint = AppTheme.colors.homeScreenButtonTint,
                onClick = interactor::onInfoButtonClicked,
            )
            ImageButton(
                resource = Resources.Settings,
                tint = AppTheme.colors.homeScreenButtonTint,
                onClick = interactor::onSettingsButtonClicked,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(KMPWindowInsets.verticalInsets)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!state.hasInitialized) {
                Spacer(modifier = Modifier.weight(1f))
            } else if (!state.hasSaveFolder) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .zIndex(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Please select a save folder for receiving files")
                    PrimaryButton(
                        label = "Select Folder",
                        onClick = interactor::onSelectSaveFolderClicked
                    )
                }
            } else if (state.discoveredDevices.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .zIndex(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Searching for devices...")
                }
            } else {
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
                                DiscoveredDevice(
                                    device = device,
                                    onClick = { interactor.onDeviceClicked(device) }
                                )
                            }
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RadiatingLogo(ringSpacing)
                Text("Visible as \"${state.displayName}\"")
                Text(
                    modifier = Modifier.padding(top = 4.dp ),
                    text = state.broadcastIp,
                    style = AppTheme.typography.ipAddress,
                )
            }
        }

        SettingsBottomSheet(
            isVisible = state.isSettingsBottomSheetVisible,
            onDismissRequest = interactor::onSettingsBottomSheetDismissed,
        )

        AddDeviceModal(
            isVisible = state.isAddDeviceModalVisible,
            onDismissRequest = interactor::onAddDeviceModalDismissed,
        )

        AboutModal(
            isVisible = state.isAboutModalVisible,
            onDismissRequest = interactor::onAboutModalDismissed,
        )

        TransferMessage()
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
        colorFilter = ColorFilter.tint(AppTheme.colors.primary),
        contentDescription = ""
    )
}