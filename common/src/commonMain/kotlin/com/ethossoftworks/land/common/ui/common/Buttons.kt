package com.ethossoftworks.land.common.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.outsidesource.oskitcompose.canvas.rememberKmpPainterResource
import com.outsidesource.oskitcompose.resources.KMPResource

@Composable
fun ImageButton(
    resource: KMPResource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .offset(x = 8.dp, y = -(8).dp)
            .clip(CircleShape)
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple()
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            modifier = Modifier.size(24.dp),
            painter = rememberKmpPainterResource(resource),
            contentDescription = null,
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