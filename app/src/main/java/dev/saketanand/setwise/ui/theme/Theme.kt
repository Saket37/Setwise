package dev.saketanand.setwise.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Volt,
    onPrimary = DarkBackground,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = Moss,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    // NavigationBar indicator + selected icon
    secondaryContainer = DarkPrimaryContainer,
    onSecondaryContainer = Volt,
    tertiary = Ember,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainerHighest,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkBackground, // no lime tint on elevated surfaces
    surfaceDim = DarkBackground,
    surfaceBright = DarkSurfaceContainerHighest,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    inverseSurface = DarkOnSurface,
    inverseOnSurface = DarkSurfaceContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = Moss,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = Volt,
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnPrimaryContainer,
    tertiary = EmberDeep,
    onTertiary = Color.White,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = Color.White,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightBackground,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceContainerHighest,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightBackground,
    surfaceDim = LightSurfaceContainerHighest,
    surfaceBright = LightSurfaceContainerLowest,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    inverseSurface = LightOnSurface,
    inverseOnSurface = LightBackground,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = Color.Black,
)

/** PR colour (Ember). Only use it to mark personal records. */
val ColorScheme.pr: Color get() = tertiary
val ColorScheme.onPr: Color get() = onTertiary

val MaterialTheme.spacing: Spacing
    @Composable @ReadOnlyComposable get() = LocalSpacing.current

/**
 * Setwise is dark-first. Dynamic colour is deliberately not supported so Volt and Ember
 * keep their meaning (action vs. PR) regardless of wallpaper.
 */
@Composable
fun SetwiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = SetwiseTypography,
            shapes = SetwiseShapes,
            content = content,
        )
    }
}
