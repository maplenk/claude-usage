package com.qbapps.claudeusage.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.qbapps.claudeusage.R

/**
 * Two families only: Roboto Flex carries all UI text; Roboto Mono carries anything
 * that ticks (countdowns, timestamps, key fragments). If a glyph changes while you
 * are looking at it, it is mono and tabular.
 */
private val Flex = FontFamily(
    Font(R.font.roboto_flex_variable, FontWeight.Normal),
    Font(R.font.roboto_flex_variable, FontWeight.Medium),
    Font(R.font.roboto_flex_variable, FontWeight.SemiBold),
)
private val Mono = FontFamily(
    Font(R.font.roboto_mono_variable, FontWeight.Normal),
    Font(R.font.roboto_mono_variable, FontWeight.Medium),
)

val AppTypography = Typography(
    // Hero metric slot — kept on displayLarge so existing call sites keep working.
    displayLarge = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 76.sp,
        lineHeight = 62.sp,
        letterSpacing = (-3.4).sp,
        fontFeatureSettings = "tnum",
    ),
    displayMedium = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1.8).sp,
        fontFeatureSettings = "tnum",
    ),
    displaySmall = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.9).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineMedium = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

/** App-specific styles — not part of MaterialTheme.typography. */
object OpenUsageText {
    val metricHero = AppTypography.displayLarge
    val metricLarge = AppTypography.displayMedium
    val metricMedium = AppTypography.displaySmall

    val providerLabel = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.55.sp,
    )
    val statusLabel = TextStyle(
        fontFamily = Flex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.0.sp,
    )
    val countdown = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "tnum",
    )
    val countdownSmall = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum",
    )
}
