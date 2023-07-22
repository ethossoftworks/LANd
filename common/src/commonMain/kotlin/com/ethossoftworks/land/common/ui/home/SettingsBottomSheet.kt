@file:Suppress("INLINE_FROM_HIGHER_PLATFORM") // https://youtrack.jetbrains.com/issue/KTIJ-20816/Bogus-error-Cannot-inline-bytecode-built-with-JVM-target-11-into-bytecode-that-is-being-built-with-JVM-target-1.8.

package com.ethossoftworks.land.common.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ethossoftworks.land.common.ui.common.AppTextField
import com.ethossoftworks.land.common.ui.common.PrimaryButton
import com.ethossoftworks.land.common.ui.common.theme.AppTheme
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitcompose.popup.BottomSheet
import com.outsidesource.oskitcompose.popup.BottomSheetStyles

@Composable
fun SettingsBottomSheet(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    interactor: SettingsBottomSheetViewInteractor = rememberInjectForRoute(),
) {
    val state by interactor.collectAsState()
    val styles = remember { BottomSheetStyles() }

    BottomSheet(
        isVisible = isVisible,
        styles = styles,
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SettingsSection("Server Settings") {
                SettingsField("Display Name") {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextField(
                                modifier = Modifier.weight(1f),
                                value = state.displayName,
                                readOnly = true,
                                onValueChange = {}
                            )
                            PrimaryButton(label = "Change", onClick = {})
                        }
                    }
                }
                SettingsField("Visibility") {
                    Column {
                        Text("Visible, Send, and Receive")
                        Text("Hidden, Send, and Receive")
                        Text("Send Only")
                    }
                }
            }
            SettingsSection("Receiving Files") {
                SettingsField("Save Folder") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            modifier = Modifier.weight(1f),
                            value = state.saveFolder ?: "",
                            readOnly = true,
                            singleLine = true,
                            onValueChange = {}
                        )
                        PrimaryButton(label = "Change", onClick = interactor::onSaveFolderChangeClicked)
                    }
                }
                SettingsField("Transfer Requests") {
                    Column {
                        Text("Ask for All")
                        Text("Accept All from Contacts")
                        Text("Accept All")
                    }
                }
            }
            SettingsSection("Contacts") {

            }
        }
    }
}

@Composable
private fun SettingsSection(
    header: String,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsSectionHeader(label = header)
        content()
    }
}

@Composable
private fun SettingsSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = label.uppercase(),
        style = AppTheme.typography.settingsSectionHeader,
    )
}

@Composable
private fun SettingsField(
    label: String,
    content: @Composable () -> Unit,
) {
    Column {
        SettingsFieldLabel(modifier = Modifier.padding(bottom = 6.dp), label = label)
        content()
    }
}

@Composable
private fun SettingsFieldLabel(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = label,
        style = AppTheme.typography.settingsFieldLabel,
    )
}