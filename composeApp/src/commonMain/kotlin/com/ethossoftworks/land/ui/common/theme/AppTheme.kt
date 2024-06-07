package com.ethossoftworks.land.ui.common.theme

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
            colors = MaterialTheme.colors.copy(
                primary = AppTheme.colors.primary,
            ),
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
    val primary: Color
    val onPrimary: Color
    val secondary: Color
    val tertiary: Color
    val primaryButtonBg: Color
    val secondaryButtonBg: Color
    val homeScreenButtonTint: Color
}

object AppLightTheme : AppColors {
    override val screenBackground = Color.White
    override val primary = Color(0xFF155fd4)
    override val onPrimary = Color.White
    override val secondary = Color(0xFFCCCCCC)
    override val tertiary = Color(0xFF494C50)
    override val primaryButtonBg = primary
    override val secondaryButtonBg = Color.Black.copy(alpha = .07f)
    override val homeScreenButtonTint = Color(0xFF444444)
}

data class AppDimensions internal constructor(
    val screenHPadding: Dp = 16.dp,
    val screenVPadding: Dp = 16.dp,
    val screenDefaultMaxWidth: Dp = 500.dp,
)

data class AppTypography internal constructor(
    val default: TextStyle = TextStyle(fontSize = 14.sp),
    val textButton: TextStyle = TextStyle(fontSize = 14.sp),
    val deviceTransferStatus: TextStyle = TextStyle(
        fontSize = 11.sp,
        color = Color(0xFF777777),
    ),
    val transferMessageText: TextStyle = TextStyle(
        color = Color(0xFFE6E6E6),
        fontSize = 12.sp,
    ),
    val transferMessageButtonPrimary: TextStyle = TextStyle(
        color = Color(0xFF74A4F1),
        fontSize = 14.sp,
    ),
    val transferMessageButtonSecondary: TextStyle = transferMessageButtonPrimary.copy(
        color = Color(0xFFEB9393),
    ),
    val primaryButton: TextStyle = TextStyle(
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    val secondaryButton: TextStyle = TextStyle(
        color = Color.Black,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    val formSectionHeader: TextStyle = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
    ),
    val formFieldLabel: TextStyle = TextStyle(
        fontSize = 12.sp,
        letterSpacing = .5.sp,
        color = Color(0xFF333333),
    ),
    val formFieldNote: TextStyle = TextStyle(
        fontSize = 12.sp,
        color = Color(0xFF333333),
    ),
    val formFieldNoteError: TextStyle = formFieldNote.copy(
        color = Color(0xFFCA2525)
    ),
    val ipAddress: TextStyle = TextStyle(
        fontSize = 11.sp,
        color = Color.Black.copy(alpha = .5f)
    ),
    val screenTitle: TextStyle = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 1.5.em,
    ),
    val heading1: TextStyle = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 1.5.em,
    ),
    val subHeading: TextStyle = TextStyle(
        fontSize = 12.sp,
        lineHeight = 1.5.em,
    ),
)
