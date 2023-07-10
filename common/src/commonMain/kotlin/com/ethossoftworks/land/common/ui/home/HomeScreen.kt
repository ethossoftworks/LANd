@file:Suppress("INLINE_FROM_HIGHER_PLATFORM") // https://youtrack.jetbrains.com/issue/KTIJ-20816/Bogus-error-Cannot-inline-bytecode-built-with-JVM-target-11-into-bytecode-that-is-being-built-with-JVM-target-1.8.

package com.ethossoftworks.land.common.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitcompose.systemui.KMPWindowInsets
import com.outsidesource.oskitcompose.systemui.StatusBarIconColorEffect


@Composable
fun HomeScreen(
    interactor: HomeScreenViewInteractor = rememberInjectForRoute()
) {
    val state by interactor.collectAsState()
    val density = LocalDensity.current

    StatusBarIconColorEffect(useDarkIcons = true)

    BoxWithConstraints(
        modifier = Modifier
            .background(color = Color.White)
            .padding(bottom = 16.dp)
            .windowInsetsPadding(KMPWindowInsets.systemBars)
    ) {
        val ringSpacing = maxHeight / 6f
        val sizeAnim = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            interactor.viewMounted()
        }

        LaunchedEffect(ringSpacing) {
            sizeAnim.snapTo(0f)
            sizeAnim.animateTo(
                targetValue = with(density) { ringSpacing.toPx() },
                animationSpec = infiniteRepeatable(animation = tween(durationMillis = 2_000, easing = LinearEasing)),
            )
        }

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
                            center = Offset(size.center.x, size.height)
                        )
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (device in state.discoveredDevices.values) {
                    Text(
                        modifier = Modifier
                            .clickable {

                            },
                        text = device.name
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("Visible as ${state.displayName}")
            if (!state.hasSaveFolder) {
                Text(
                    modifier = Modifier.clickable {
                        interactor.onSelectSaveFolderClicked()
                    },
                    text = "Please select a save folder"
                )
            }
        }
    }
}