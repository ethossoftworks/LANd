package com.ethossoftworks.land.common.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import com.ethossoftworks.land.common.ui.common.AppTextField
import com.ethossoftworks.land.common.ui.common.PrimaryButton
import com.ethossoftworks.land.common.ui.common.SecondaryButton
import com.ethossoftworks.land.common.ui.common.form.FormField
import com.ethossoftworks.land.common.ui.common.form.FormFieldNote
import com.ethossoftworks.land.common.ui.common.form.FormSection
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitcompose.popup.Modal
import org.koin.core.parameter.parametersOf

@Composable
fun AddDeviceModal(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    interactor: AddDeviceModalViewInteractor = rememberInjectForRoute { parametersOf(onDismissRequest) }
) {
    val state = interactor.collectAsState()

    Modal(
        isVisible = isVisible,
        onDismissRequest = interactor::onCancelled,
        onPreviewKeyEvent = {
            if (it.key == Key.Enter || it.key == Key.NumPadEnter) {
                interactor.onAddClicked()
                return@Modal true
            }
            return@Modal false
        }
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
        ) {
            FormSection("Add Device") {
                FormField("IP Address") {
                    AppTextField(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.connectionState != ManualDeviceConnectionState.Connecting,
                        value = state.ipAddress,
                        singleLine = true,
                        onValueChange = interactor::onIpAddressChanged
                    )

                    if (state.connectionState != ManualDeviceConnectionState.Idle) {
                        FormFieldNote(
                            modifier = Modifier.padding(top = 4.dp),
                            note = state.connectionState.toString(),
                            isError = state.connectionState == ManualDeviceConnectionState.Error,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                SecondaryButton(
                    label = "Cancel",
                    onClick = interactor::onCancelled
                )
                PrimaryButton(
                    label = "Add",
                    onClick = interactor::onAddClicked,
                )
            }
        }
    }
}