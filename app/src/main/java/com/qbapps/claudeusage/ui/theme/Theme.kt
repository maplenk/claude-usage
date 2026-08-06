package com.qbapps.claudeusage.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)

// --------------- Status color getters (legacy names → guardrail system) ---------------

/** Green when usage is safe. */
val statusSafeColor: Color
    @Composable get() = LocalOpenUsageColors.current.normal.fg

/** Yellow/amber when usage is elevated. */
val statusModerateColor: Color
    @Composable get() = LocalOpenUsageColors.current.elevated.fg

/** Red when usage is critical. */
val statusCriticalColor: Color
    @Composable get() = LocalOpenUsageColors.current.critical.fg

val statusSafeContainerColor: Color
    @Composable get() = LocalOpenUsageColors.current.normal.container

val statusModerateContainerColor: Color
    @Composable get() = LocalOpenUsageColors.current.elevated.container

val statusCriticalContainerColor: Color
    @Composable get() = LocalOpenUsageColors.current.critical.container

val statusHighColor: Color
    @Composable get() = LocalOpenUsageColors.current.high.fg

val statusHighContainerColor: Color
    @Composable get() = LocalOpenUsageColors.current.high.container

val statusUnknownColor: Color
    @Composable get() = LocalOpenUsageColors.current.unknown.fg

val statusUnknownContainerColor: Color
    @Composable get() = LocalOpenUsageColors.current.unknown.container

val grokAccentColor: Color
    @Composable get() = LocalOpenUsageColors.current.grok

val grokContainerColor: Color
    @Composable get() = if (isSystemInDarkTheme()) GrokContainerDark else GrokContainerLight

@Composable
fun ClaudeUsageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    // Codex must stay a distinct hue even under wallpaper theming — two providers
    // must never collapse into one wallpaper hue.
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        base.copy(
            secondary = if (darkTheme) SecondaryDark else SecondaryLight,
            secondaryContainer = if (darkTheme) SecondaryContainerDark else SecondaryContainerLight,
            onSecondaryContainer = if (darkTheme) OnSecondaryContainerDark else OnSecondaryContainerLight,
        )
    } else {
        base
    }

    CompositionLocalProvider(
        LocalOpenUsageColors provides if (darkTheme) DarkOpenUsageColors else LightOpenUsageColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
