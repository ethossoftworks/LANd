package com.ethossoftworks.land.common.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethossoftworks.land.common.ui.common.theme.AppTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    BaseButton(
        label,
        modifier,
        AppTheme.colors.primaryButtonBg,
        AppTheme.typography.primaryButton,
        onClick
    )
}

@Composable
fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    BaseButton(
        label,
        modifier,
        AppTheme.colors.secondaryButtonBg,
        AppTheme.typography.secondaryButton,
        onClick
    )
}

@Composable
private fun BaseButton(
    label: String,
    modifier: Modifier = Modifier,
    buttonColor: Color,
    textStyle: TextStyle,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(buttonColor)
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(),
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = textStyle,
        )
    }
}



@OptIn(ExperimentalResourceApi::class)
@Composable
fun ImageButton(
    resource: DrawableResource,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = AppTheme.colors.primary)
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            modifier = Modifier.size(20.dp),
            painter = painterResource(resource),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint)
        )
    }
}

@Composable
fun TextButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        text = label,
        fontSize = 14.sp,
    )
}