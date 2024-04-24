package com.ethossoftworks.land.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ethossoftworks.land.BuildInfo
import com.ethossoftworks.land.ui.common.theme.AppTheme
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitcompose.popup.Modal
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import land.composeapp.generated.resources.Res
import land.composeapp.generated.resources.land_logo
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalResourceApi::class)
@Composable
fun AboutModal(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
) {
    Modal(
        isVisible = isVisible,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "LANd", style = AppTheme.typography.heading1)
                Text(
                    text = "Version ${BuildInfo.version} (${BuildInfo.buildNumber})",
                    style = AppTheme.typography.subHeading
                )
                Text(
                    text = "Copyright \u00a9 ${
                        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
                    } Ethos Softworks",
                    style = AppTheme.typography.subHeading
                )
            }
            Box(modifier = Modifier.padding(24.dp)) {
                Image(
                    modifier = Modifier.size(128.dp),
                    painter = painterResource(Res.drawable.land_logo),
                    contentDescription = "Logo",
                )
            }
        }
    }
}