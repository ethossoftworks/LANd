package com.ethossoftworks.land.ui.common.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ethossoftworks.land.ui.common.theme.AppTheme

@Composable
fun FormSection(
    header: String,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FormSectionHeader(label = header)
        content()
    }
}

@Composable
fun FormSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = label.uppercase(),
        style = AppTheme.typography.formSectionHeader,
    )
}

@Composable
fun FormField(
    label: String,
    content: @Composable () -> Unit,
) {
    Column {
        FormFieldLabel(modifier = Modifier.padding(bottom = 6.dp), label = label)
        content()
    }
}

@Composable
fun FormFieldLabel(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = label.uppercase(),
        style = AppTheme.typography.formFieldLabel,
    )
}

@Composable
fun FormFieldNote(
    note: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Text(
        modifier = modifier,
        text = note,
        style = if (!isError) AppTheme.typography.formFieldNote else AppTheme.typography.formFieldNoteError,
    )
}