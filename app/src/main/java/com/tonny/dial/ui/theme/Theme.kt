package com.tonny.dial.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** iOS system colors, light and dark variants. */
@Immutable
data class IosColors(
    val dark: Boolean,
    val background: Color,
    val groupedBackground: Color,
    val card: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,
    val blue: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    /** Tint laid over Liquid Glass surfaces. */
    val glassTint: Color,
    /** Fill for glass controls that sit on a plain background (keys, tiles). */
    val controlTint: Color,
)

val LightIos = IosColors(
    dark = false,
    background = Color(0xFFFFFFFF),
    groupedBackground = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    fill = Color(0x1F787880),
    blue = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    glassTint = Color(0xB3FFFFFF),
    controlTint = Color(0xFFE5E5EA),
)

val DarkIos = IosColors(
    dark = true,
    background = Color(0xFF000000),
    groupedBackground = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x99545458),
    fill = Color(0x3D767680),
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    glassTint = Color(0x26FFFFFF),
    controlTint = Color(0xFF2C2C2E),
)

val LocalIos = staticCompositionLocalOf { LightIos }

object Ios {
    val colors: IosColors
        @Composable @ReadOnlyComposable get() = LocalIos.current
}

/** iOS text styles (SF Pro metrics approximated on the system sans). */
object IosType {
    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    val title1 = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    val title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.2).sp)
    val callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp)
    val subhead = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val caption2 = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
}

@Composable
fun DialTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ios = if (dark) DarkIos else LightIos
    val scheme = if (dark) {
        darkColorScheme(
            primary = ios.blue, background = ios.background, surface = ios.background,
            onSurface = ios.label, onSurfaceVariant = ios.secondaryLabel, error = ios.red,
        )
    } else {
        lightColorScheme(
            primary = ios.blue, background = ios.background, surface = ios.background,
            onSurface = ios.label, onSurfaceVariant = ios.secondaryLabel, error = ios.red,
        )
    }
    CompositionLocalProvider(LocalIos provides ios) {
        MaterialTheme(colorScheme = scheme, typography = Typography(bodyLarge = IosType.body), content = content)
    }
}
