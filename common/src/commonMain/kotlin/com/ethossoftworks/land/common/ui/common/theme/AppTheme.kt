package com.ethossoftworks.land.common.ui.common.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LocalTypography = staticCompositionLocalOf { AppTypography() }
private val LocalDimensions = staticCompositionLocalOf { AppDimensions() }
private val LocalColors = staticCompositionLocalOf { AppLightTheme }

object AppTheme {
    val dimensions
        @Composable
        @ReadOnlyComposable
        get() = LocalDimensions.current

    val typography
        @Composable
        @ReadOnlyComposable
        get() = LocalTypography.current

    val colors
        @Composable
        @ReadOnlyComposable
        get() = LocalColors.current
}

@Composable
fun AppTheme(
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(
        LocalColors provides AppTheme.colors,
        LocalTypography provides AppTheme.typography,
        LocalDimensions provides AppTheme.dimensions,
    ) {
        MaterialTheme(
            typography = Typography(
                defaultFontFamily = AppTheme.typography.default.fontFamily ?: FontFamily.Default,
                body1 = AppTheme.typography.default,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.colors.screenBackground),
                content = content,
            )
        }
    }
}

interface AppColors {
    val screenBackground: Color
}

object AppLightTheme : AppColors {
    override val screenBackground = Color.White
}

data class AppDimensions internal constructor(
    val screenHPadding: Dp = 16.dp,
    val screenVPadding: Dp = 16.dp,
    val screenDefaultMaxWidth: Dp = 500.dp,
)

data class AppTypography internal constructor(
    val default: TextStyle = TextStyle(fontSize = 14.sp),
    val textButton: TextStyle = TextStyle(fontSize = 14.sp),
    val requestText: TextStyle = TextStyle(
        color = Color.White,
        fontSize = 12.sp,
    )
)
