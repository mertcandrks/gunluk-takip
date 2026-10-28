package com.uykutakip.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val AppBackground = Color(0xFF080D14)
val AppSurface = Color(0xFF111A25)
val AppSurfaceHigh = Color(0xFF182433)
val SleepAccent = Color(0xFFAA8DFF)
val MealAccent = Color(0xFF45D6A8)
val WaterAccent = Color(0xFF4FC3F7)
val WarmAccent = Color(0xFFFFA45B)
val CoralAccent = Color(0xFFFF7D73)
val TextPrimary = Color(0xFFF4F7FB)
val TextSecondary = Color(0xFFAAB8C8)

private val AppColors = darkColorScheme(
    primary = SleepAccent,
    onPrimary = Color(0xFF1C103D),
    secondary = MealAccent,
    onSecondary = Color(0xFF002D22),
    tertiary = WarmAccent,
    onTertiary = Color(0xFF351800),
    background = AppBackground,
    onBackground = TextPrimary,
    surface = AppSurface,
    onSurface = TextPrimary,
    surfaceVariant = AppSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    error = CoralAccent,
)

@Composable
fun DailyTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        ),
        content = content,
    )
}

