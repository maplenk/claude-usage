package com.qbapps.claudeusage.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

// --------------- Status color getters ---------------

/** Green when usage is safe. */
val statusSafeColor: Color
    @Composable get() = if (isSystemInDarkTheme()) StatusSafeDark else StatusSafeLight

/** Yellow/amber when usage is moderate. */
val statusModerateColor: Color
    @Composable get() = if (isSystemInDarkTheme()) StatusModerateDark else StatusModerateLight

/** Red when usage is critical. */
val statusCriticalColor: Color
    @Composable get() = if (isSystemInDarkTheme()) StatusCriticalDark else StatusCriticalLight

val statusSafeContainerColor: Color
    @Composable get() = if (isSystemInDarkTheme()) StatusSafeContainerDark else StatusSafeContainerLight

val statusModerateContainerColor: Color
    @Composable get() = if (isSystemInDarkTheme()) StatusModerateContainerDark else StatusModerateContainerLight

val statusCriticalContainerColor: Color
    @Composable get() = if (isSystemInDarkTheme()) StatusCriticalContainerDark else StatusCriticalContainerLight

val statusHighColor: Color
    @Composable get() = if (isSystemInDarkTheme()) StatusHighDark else StatusHighLight

val statusHighContainerColor: Color
    @Composable get() = if (isSystemInDarkTheme()) StatusHighContainerDark else StatusHighContainerLight

val statusUnknownColor: Color
    @Composable get() = if (isSystemInDarkTheme()) StatusUnknownDark else StatusUnknownLight

val statusUnknownContainerColor: Color
    @Composable get() = if (isSystemInDarkTheme()) StatusUnknownContainerDark else StatusUnknownContainerLight

val grokAccentColor: Color
    @Composable get() = if (isSystemInDarkTheme()) GrokAccentDark else GrokAccentLight

val grokContainerColor: Color
    @Composable get() = if (isSystemInDarkTheme()) GrokContainerDark else GrokContainerLight

@Composable
fun ClaudeUsageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
