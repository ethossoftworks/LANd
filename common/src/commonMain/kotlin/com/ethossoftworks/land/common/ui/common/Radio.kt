package com.ethossoftworks.land.common.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ethossoftworks.land.common.ui.common.theme.AppTheme

@Composable
fun Radio(
    label: String? = null,
    isSelected: Boolean = false,
    isDisabled: Boolean = false,
    size: Dp = 20.dp,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick, enabled = !isDisabled)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .indication(interactionSource, indication = rememberRipple())
                .size(size)
                .border(
                    width = 2.dp,
                    color = if (isSelected) colors.primary else colors.secondary,
                    shape = CircleShape
                )
                .run {
                    if (!isSelected) return@run this
                    drawWithContent {
                        drawCircle(color = colors.primary, radius = (size / 4).toPx())
                    }
                }
        )
        if (label != null) {
            Text(
                text = label,
            )
        }
    }
}