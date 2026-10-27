package com.truyenaudio.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF0B1F2C)
val Mint = Color(0xFF8FF0D0)
val Teal = Color(0xFF087F72)

private val Day = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F5EA), onPrimaryContainer = Color(0xFF034F46),
    secondary = Color(0xFF4B626C), secondaryContainer = Color(0xFFE4EFF2), onSecondaryContainer = Ink,
    background = Color(0xFFF4F7F6), onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFEAF0EE), onSurfaceVariant = Color(0xFF63746F),
    outline = Color(0xFFB5C8C1), outlineVariant = Color(0xFFE0E9E5),
    error = Color(0xFFB53B43), errorContainer = Color(0xFFFFEBEC), onErrorContainer = Color(0xFF78242B)
)
private val Night = darkColorScheme(
    primary = Mint, onPrimary = Color(0xFF073C35),
    primaryContainer = Color(0xFF153E37), onPrimaryContainer = Color(0xFFB8F7DF),
    secondary = Color(0xFFABCBD1), secondaryContainer = Color(0xFF213540), onSecondaryContainer = Color(0xFFD8ECEF),
    background = Color(0xFF0B141B), onBackground = Color(0xFFE4EEE9),
    surface = Color(0xFF132129), onSurface = Color(0xFFE4EEE9),
    surfaceVariant = Color(0xFF1A2A31), onSurfaceVariant = Color(0xFF9DB3AE),
    outline = Color(0xFF4E6861), outlineVariant = Color(0xFF293D39),
    error = Color(0xFFFFB2B7), errorContainer = Color(0xFF4C272D), onErrorContainer = Color(0xFFFFDADD)
)
private val Type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 35.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 31.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp)
)

@Composable
fun TruyenAudioTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Night else Day, typography = Type,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(32.dp)),
        content = content)
}
