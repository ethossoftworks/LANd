package com.ethossoftworks.land.ui.app

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.ethossoftworks.land.DIPlatformContext
import com.ethossoftworks.land.initDI
import com.ethossoftworks.land.ui.common.theme.AppTheme
import org.koin.core.module.Module

@Composable
actual fun PreviewWrapper(
    vararg extraModules: Module,
    content: @Composable BoxScope.() -> Unit,
) {
    initDI(
        platformContext = DIPlatformContext(),
        extraModules = arrayOf(*extraModules),
    )
    AppTheme(content = content)
}
