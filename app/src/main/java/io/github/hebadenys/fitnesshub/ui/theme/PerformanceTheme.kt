package io.github.hebadenys.fitnesshub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Performance B tokens. Scoped to the first redesigned surfaces during rollout. */
internal val PerformanceColorScheme = darkColorScheme(
    primary = Color(0xFFC6F26B),
    onPrimary = Color(0xFF11161C),
    primaryContainer = Color(0xFF2F3C20),
    onPrimaryContainer = Color(0xFFC6F26B),
    secondary = Color(0xFFBAC7D5),
    onSecondary = Color(0xFF11161C),
    secondaryContainer = Color(0xFF293541),
    onSecondaryContainer = Color(0xFFF4F7FB),
    tertiary = Color(0xFF61C5F5),
    onTertiary = Color(0xFF11161C),
    tertiaryContainer = Color(0xFF12384A),
    onTertiaryContainer = Color(0xFFB9E8FF),
    background = Color(0xFF11161C),
    onBackground = Color(0xFFF4F7FB),
    surface = Color(0xFF1D252E),
    onSurface = Color(0xFFF4F7FB),
    surfaceVariant = Color(0xFF293541),
    onSurfaceVariant = Color(0xFFBAC7D5),
    surfaceContainerLowest = Color(0xFF11161C),
    surfaceContainerLow = Color(0xFF1D252E),
    surfaceContainer = Color(0xFF1D252E),
    surfaceContainerHigh = Color(0xFF293541),
    surfaceContainerHighest = Color(0xFF354352),
    surfaceTint = Color(0xFFC6F26B),
    outline = Color(0xFF8595A7),
    outlineVariant = Color(0xFF354352),
    inverseSurface = Color(0xFFF4F7FB),
    inverseOnSurface = Color(0xFF11161C),
    inversePrimary = Color(0xFF405A11),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C2422),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
internal fun PerformanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PerformanceColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}
