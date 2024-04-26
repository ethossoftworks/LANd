package com.ethossoftworks.land.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ethossoftworks.land.service.preferences.DeviceVisibility
import com.ethossoftworks.land.service.preferences.TransferRequestPermissionType
import com.ethossoftworks.land.ui.common.*
import com.ethossoftworks.land.ui.common.form.FormField
import com.ethossoftworks.land.ui.common.form.FormSection
import com.ethossoftworks.land.ui.common.theme.AppTheme
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
    val state = interactor.collectAsState()
    val styles = remember { BottomSheetStyles() }

    BottomSheet(
        modifier = Modifier.windowInsetsPadding(KMPWindowInsets.bottomInsets),
        isVisible = isVisible,
        styles = styles,
        onDismissRequest = onDismissRequest,
    ) {
        Box(
            modifier = Modifier
                .offset(y = -(8).dp)
                .align(Alignment.TopCenter)
                .width(150.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(AppTheme.colors.secondary)
        )
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            FormSection("Server Settings") {
                FormField("Display Name") {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppTextField(
                                modifier = Modifier.weight(1f),
                                value = if (state.isEditingDisplayName) state.editableDisplayName else state.displayName,
                                readOnly = !state.isEditingDisplayName,
                                maxLength = 255,
                                singleLine = true,
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
                        for (visibility in DeviceVisibility.entries) {
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppTextField(
                            modifier = Modifier.weight(1f),
                            value = state.saveFolder?.name ?: "",
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