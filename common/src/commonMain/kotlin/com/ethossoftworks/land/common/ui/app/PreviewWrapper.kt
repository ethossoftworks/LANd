package com.ethossoftworks.land.common.ui.app

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import org.koin.core.module.Module

@Composable
expect fun PreviewWrapper(
    vararg extraModules: Module,
    content: @Composable BoxScope.() -> Unit,
)
