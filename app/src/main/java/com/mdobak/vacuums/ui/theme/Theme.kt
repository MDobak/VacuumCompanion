package com.mdobak.vacuums.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class AppColors(
    val isDark: Boolean,
    val heroTop: Color,
    val heroBottom: Color,
    val glow: Color,
    val success: Color,
    val warning: Color,
    val textSecondary: Color,
    val divider: Color,
    // Map colors
    val mapBackground: Color,
    val mapFloor: Color,
    val mapWall: Color,
    val mapSegments: List<Color>,
    val mapPath: Color,
    val mapCoverage: Color,
    val noGo: Color,
    val noMop: Color,
    val wall: Color,
    val zone: Color,
    val annotation: Color,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF2A541),
    onPrimary = Color(0xFF221402),
    primaryContainer = Color(0xFF3A2A12),
    onPrimaryContainer = Color(0xFFFFD9A6),
    secondary = Color(0xFF8FB3E0),
    onSecondary = Color(0xFF0B1A2C),
    secondaryContainer = Color(0xFF233247),
    onSecondaryContainer = Color(0xFFD3E3F8),
    tertiary = Color(0xFF6FD6B5),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE8ECF2),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE8ECF2),
    surfaceVariant = Color(0xFF1F2630),
    onSurfaceVariant = Color(0xFF9AA4B2),
    surfaceContainerLowest = Color(0xFF0A0D11),
    surfaceContainerLow = Color(0xFF13181F),
    surfaceContainer = Color(0xFF171C24),
    surfaceContainerHigh = Color(0xFF1E252F),
    surfaceContainerHighest = Color(0xFF262E3A),
    outline = Color(0xFF3A4454),
    outlineVariant = Color(0xFF262E3A),
    error = Color(0xFFFF7A7A),
    onError = Color(0xFF2A0606),
    errorContainer = Color(0xFF3B1517),
    onErrorContainer = Color(0xFFFFD7D7),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFB86A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3BF),
    onPrimaryContainer = Color(0xFF2E1A00),
    secondary = Color(0xFF3D6595),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7F6),
    onSecondaryContainer = Color(0xFF0F2540),
    tertiary = Color(0xFF167A5F),
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF12171E),
    surface = Color(0xFFF3F5F8),
    onSurface = Color(0xFF12171E),
    surfaceVariant = Color(0xFFE4E8EE),
    onSurfaceVariant = Color(0xFF5B6574),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F2F6),
    surfaceContainerHighest = Color(0xFFE6E9EF),
    outline = Color(0xFFC3CAD4),
    outlineVariant = Color(0xFFE2E6EC),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFDE2E2),
    onErrorContainer = Color(0xFF4A0B0B),
)

private val DarkApp = AppColors(
    isDark = true,
    heroTop = Color(0xFF2B3546),
    heroBottom = Color(0xFF171C24),
    glow = Color(0xFFF2A541),
    success = Color(0xFF4CC38A),
    warning = Color(0xFFF2A541),
    textSecondary = Color(0xFF9AA4B2),
    divider = Color(0xFF262E3A),
    mapBackground = Color(0xFF0E1116),
    mapFloor = Color(0xFF2A3341),
    mapWall = Color(0xFFB9C3D1),
    mapSegments = listOf(
        Color(0xFF3E6A9E), Color(0xFF3C8577), Color(0xFF7A5C9C),
        Color(0xFFA0763C), Color(0xFF4F7F3F), Color(0xFF9A4F62),
    ),
    mapPath = Color(0xCCFFFFFF),
    mapCoverage = Color(0x99061226),
    noGo = Color(0xFFEF4444),
    noMop = Color(0xFFD946EF),
    wall = Color(0xFFEF4444),
    zone = Color(0xFF4FA3FF),
    annotation = Color(0xFF10B981),
)

private val LightApp = DarkApp.copy(
    isDark = false,
    heroTop = Color(0xFFFFFFFF),
    heroBottom = Color(0xFFE9EEF5),
    glow = Color(0xFFF2A541),
    textSecondary = Color(0xFF5B6574),
    divider = Color(0xFFE2E6EC),
    mapBackground = Color(0xFFF3F5F8),
    mapFloor = Color(0xFFD5DBE3),
    mapWall = Color(0xFF3B4452),
    mapSegments = listOf(
        Color(0xFF8DB4E2), Color(0xFF86CDB9), Color(0xFFBDA5DD),
        Color(0xFFEBC48D), Color(0xFFA7CF8F), Color(0xFFE5A3B3),
    ),
    mapPath = Color(0xCC1B2330),
    mapCoverage = Color(0x8C0B2A5C),
)

val LocalAppColors = staticCompositionLocalOf { DarkApp }

object Theme {
    val colors: AppColors @Composable get() = LocalAppColors.current
}

enum class Appearance { System, Dark, Light }

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun VacuumCompanionTheme(appearance: Appearance = Appearance.System, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        Appearance.System -> isSystemInDarkTheme()
        Appearance.Dark -> true
        Appearance.Light -> false
    }
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkApp else LightApp) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
