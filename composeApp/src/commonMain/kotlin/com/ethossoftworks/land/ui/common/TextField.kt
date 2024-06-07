package com.ethossoftworks.land.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun AppTextField(
    value: String,
    onValueChange: (value: String) -> Unit,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    maxLength: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        modifier = modifier,
        value = value.take(maxLength),
        readOnly = readOnly,
        enabled = enabled,
        singleLine = singleLine,
        onValueChange = { onValueChange(it.take(maxLength)) },
        decorationBox = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .height(40.dp)
                    .background(Color.Black.copy(alpha = .07f))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                it()
            }
        }
    )
}