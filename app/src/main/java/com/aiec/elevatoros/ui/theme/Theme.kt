package com.aiec.elevatoros.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/* AIEC identity kept from the original: deep navy, lift-door gold, warm cream — with large, high-contrast type. */
object Aiec {
    val Navy = Color(0xFF0F1B33)
    val Navy2 = Color(0xFF1C2D52)
    val Gold = Color(0xFFF2B705)
    val GoldDeep = Color(0xFFC99400)
    val Cream = Color(0xFFFFF8EC)
    val Ok = Color(0xFF1E8E3E)
    val Warn = Color(0xFFE37400)
    val Late = Color(0xFFD93025)
    val Paused = Color(0xFF8A8F98)
}

private val Light = lightColorScheme(
    primary = Aiec.Navy, onPrimary = Color.White, primaryContainer = Aiec.Navy2, onPrimaryContainer = Color.White,
    secondary = Aiec.Gold, onSecondary = Aiec.Navy, secondaryContainer = Color(0xFFFFE8A3), onSecondaryContainer = Aiec.Navy,
    background = Aiec.Cream, onBackground = Color(0xFF1A1C1E), surface = Color.White, onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFF3EBDD), onSurfaceVariant = Color(0xFF4A4639), outline = Color(0xFFCFC6B4),
    error = Aiec.Late, onError = Color.White, tertiary = Aiec.Ok, onTertiary = Color.White,
)

private val Dark = darkColorScheme(
    primary = Aiec.Gold, onPrimary = Aiec.Navy, primaryContainer = Color(0xFF2A3A5F), onPrimaryContainer = Color.White,
    secondary = Aiec.Gold, onSecondary = Aiec.Navy, secondaryContainer = Color(0xFF4A3B00), onSecondaryContainer = Color(0xFFFFE8A3),
    background = Color(0xFF0B1426), onBackground = Color(0xFFE6E2D9), surface = Color(0xFF15213B), onSurface = Color(0xFFE6E2D9),
    surfaceVariant = Color(0xFF22304F), onSurfaceVariant = Color(0xFFC9C5BA), outline = Color(0xFF4B5874),
    error = Color(0xFFFF8A80), onError = Color.Black, tertiary = Color(0xFF81C995), onTertiary = Color.Black,
)

/** Strong contrast for bright sun / weak eyes (original "strong contrast" colours). */
private val Contrast = lightColorScheme(
    primary = Color.Black, onPrimary = Color.White, primaryContainer = Color.Black, onPrimaryContainer = Color(0xFFFFD600),
    secondary = Color(0xFFFFD600), onSecondary = Color.Black, secondaryContainer = Color(0xFFFFD600), onSecondaryContainer = Color.Black,
    background = Color.White, onBackground = Color.Black, surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color.Black, outline = Color.Black,
    error = Color(0xFFB00020), onError = Color.White, tertiary = Color(0xFF006400), onTertiary = Color.White,
)

private val AiecType = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
)

@Composable
fun AiecTheme(theme: String = "system", content: @Composable () -> Unit) {
    val scheme: ColorScheme = when (theme) {
        "light" -> Light; "dark" -> Dark; "contrast" -> Contrast
        else -> if (isSystemInDarkTheme()) Dark else Light
    }
    MaterialTheme(colorScheme = scheme, typography = AiecType, content = content)
}
