package com.ethossoftworks.land.common.ui.common

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ethossoftworks.land.common.ui.common.theme.AppTheme
import com.outsidesource.oskitcompose.animation.TransitionAnimatedContent
import com.outsidesource.oskitcompose.modifier.outerShadow
import com.outsidesource.oskitcompose.systemui.KMPWindowInsets
import com.outsidesource.oskitcompose.systemui.bottomInsets

@Composable
fun <T> InfoMessage(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(state: T) -> Unit,
) {
    TransitionAnimatedContent(
        modifier = modifier,
        targetState = targetState,
    ) { state, transition ->
        if (state == null) return@TransitionAnimatedContent
        val alphaAnim by transition.animateFloat(transitionSpec = { tween(250) }) { if (it == state) 1f else 0f }
        val positionAnim by transition.animateFloat(transitionSpec = { tween(250) }) { if (it == state) 0f else 10f }

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
                .background(AppTheme.colors.tertiary)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            content(state)
        }
    }
}