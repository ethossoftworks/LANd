@file:Suppress("INLINE_FROM_HIGHER_PLATFORM") // https://youtrack.jetbrains.com/issue/KTIJ-20816/Bogus-error-Cannot-inline-bytecode-built-with-JVM-target-11-into-bytecode-that-is-being-built-with-JVM-target-1.8.

package com.ethossoftworks.land.common.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ethossoftworks.land.BuildInfo
import com.ethossoftworks.land.common.ui.common.theme.AppTheme
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitcompose.popup.Modal
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun AboutModal(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    interactor: AboutModalViewInteractor = rememberInjectForRoute()
) {
    Modal(
        isVisible = isVisible,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .heightIn(min = 300.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "LANd", style = AppTheme.typography.heading1)
            Text(text = "Version ${BuildInfo.version} (${BuildInfo.buildNumber})", style = AppTheme.typography.subHeading)
            Text(
                text ="Copyright \u00a9 ${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year} Ethos Softworks",
                style = AppTheme.typography.subHeading
            )
        }
    }
}