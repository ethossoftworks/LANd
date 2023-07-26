@file:Suppress("INLINE_FROM_HIGHER_PLATFORM") // https://youtrack.jetbrains.com/issue/KTIJ-20816/Bogus-error-Cannot-inline-bytecode-built-with-JVM-target-11-into-bytecode-that-is-being-built-with-JVM-target-1.8.

package com.ethossoftworks.land.common.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ethossoftworks.land.common.service.preferences.DeviceVisibility
import com.ethossoftworks.land.common.service.preferences.TransferRequestPermissionType
import com.ethossoftworks.land.common.ui.common.AppTextField
import com.ethossoftworks.land.common.ui.common.PrimaryButton
import com.ethossoftworks.land.common.ui.common.Radio
import com.ethossoftworks.land.common.ui.common.SecondaryButton
import com.ethossoftworks.land.common.ui.common.form.FormField
import com.ethossoftworks.land.common.ui.common.form.FormSection
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitcompose.popup.BottomSheet
import com.outsidesource.oskitcompose.popup.BottomSheetStyles
import com.outsidesource.oskitcompose.systemui.KMPWindowInsets
import com.outsidesource.oskitcompose.systemui.bottomInsets

@Composable
fun SettingsBottomSheet(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    interactor: SettingsBottomSheetViewInteractor = rememberInjectForRoute(),
) {
    val state by interactor.collectAsState()
    val styles = remember { BottomSheetStyles() }

    BottomSheet(
        modifier = Modifier.windowInsetsPadding(KMPWindowInsets.bottomInsets),
        isVisible = isVisible,
        styles = styles,
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            FormSection("Server Settings") {
                FormField("Display Name") {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextField(
                                modifier = Modifier.weight(1f),
                                value = if (state.isEditingDisplayName) state.editableDisplayName else state.displayName,
                                readOnly = !state.isEditingDisplayName,
                                onValueChange = interactor::onEditableDisplayNameChanged,
                            )
                            if (state.isEditingDisplayName) {
                                SecondaryButton(
                                    label = "Cancel",
                                    onClick = interactor::onCancelDisplayNameClicked
                                )
                            }
                            PrimaryButton(
                                label = if (state.isEditingDisplayName) "Save" else "Change",
                                onClick = interactor::onChangeDisplayNameClicked
                            )
                        }
                    }
                }
                FormField("Visibility") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (visibility in DeviceVisibility.values()) {
                            Radio(
                                label = visibility.toViewString(),
                                isSelected = state.deviceVisibility == visibility,
                                onClick = { interactor.onDeviceVisibilityChanged(visibility) },
                            )
                        }
                    }
                }
            }
            FormSection("Receiving Files") {
                FormField("Save Folder") {
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
//                SettingsField("Transfer Requests") {
//                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
//                        for (type in TransferRequestPermissionType.values()) {
//                            Radio(
//                                label = type.toViewString(),
//                                isSelected = state.transferRequestPermissionType == type,
//                                onClick = { interactor.onTransferRequestPermissionTypeChanged(type) },
//                            )
//                        }
//                    }
//                }
            }
//            SettingsSection("Contacts") {
//                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//                    AppTextField(
//                        modifier = Modifier.weight(1f),
//                        value = state.contactToAdd,
//                        singleLine = true,
//                        onValueChange = interactor::onContactToAddChanged
//                    )
//                    PrimaryButton(label = "Add", onClick = interactor::onAddContactClicked)
//                }
//            }
        }
    }
}

private fun DeviceVisibility.toViewString() = when(this) {
    DeviceVisibility.Visible -> "Visible"
    DeviceVisibility.Hidden -> "Hidden"
    DeviceVisibility.SendOnly -> "Send Only"
}

private fun TransferRequestPermissionType.toViewString() = when(this) {
    TransferRequestPermissionType.AskAll -> "Ask for All"
    TransferRequestPermissionType.AcceptContacts -> "Accept All from Contacts"
    TransferRequestPermissionType.AcceptAll -> "Accept All"
}