package com.ethossoftworks.land.ui.openSourceLicenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ethossoftworks.land.service.openSourceLicenses.OpenSourceDependency
import com.ethossoftworks.land.ui.common.theme.AppTheme
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitcompose.modifier.outerShadow
import com.outsidesource.oskitcompose.scrollbars.KMPScrollbarStyle
import com.outsidesource.oskitcompose.scrollbars.KMPVerticalScrollbar
import com.outsidesource.oskitcompose.scrollbars.rememberKmpScrollbarAdapter
import com.outsidesource.oskitcompose.systemui.*

@Composable
fun OpenSourceLicensesScreen(
    interactor: OpenSourceLicensesViewInteractor = rememberInjectForRoute()
) {
    val state = interactor.collectAsState()
    val scrollState = rememberLazyListState()

    LaunchedEffect(Unit) {
        interactor.onViewMounted()
    }

    SystemBarColorEffect(
        statusBarIconColor = SystemBarIconColor.Light,
    )

    Column(
        modifier = Modifier
            .background(AppTheme.colors.screenBackground)
            .fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .outerShadow(blur = 8.dp, color = Color.Black.copy(alpha = .5f))
                .background(AppTheme.colors.primary)
                .padding(horizontal = 8.dp)
                .windowInsetsPadding(KMPWindowInsets.topInsets),
            contentAlignment = Alignment.CenterStart,
        ) {
            IconButton(
                onClick = interactor::onBackClicked,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppTheme.colors.onPrimary,
                )
            }
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = "Open Source Licenses",
                style = AppTheme.typography.screenTitle,
                color = AppTheme.colors.onPrimary,
            )
        }
        Box {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                state = scrollState,
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = KMPWindowInsets.bottomInsets.asPaddingValues().calculateBottomPadding()
                ),
            ) {
                items(state.dependencies) { dependency ->
                    OpenSourceDependencyItem(dependency)
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.dp,
                        color = Color.Black.copy(alpha = .1f)
                    )
                }
            }
            KMPVerticalScrollbar(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd),
                adapter = rememberKmpScrollbarAdapter(scrollState),
                style = KMPScrollbarStyle(thickness = 6.dp, minimalHeight = 32.dp)
            )
        }
    }
}

@Composable
private fun OpenSourceDependencyItem(
    dependency: OpenSourceDependency,
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable {
                    if (dependency.url.isEmpty()) return@clickable
                    uriHandler.openUri(dependency.url)
                }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            text = "${dependency.name} (${dependency.version})",
            style = AppTheme.typography.default.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable {
                    if (dependency.licenseUrl.isEmpty()) return@clickable
                    uriHandler.openUri(dependency.licenseUrl)
                }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            text = dependency.license,
            style = AppTheme.typography.subHeading,
        )
    }
}