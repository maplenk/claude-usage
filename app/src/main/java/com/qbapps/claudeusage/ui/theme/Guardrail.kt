package com.qbapps.claudeusage.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.qbapps.claudeusage.R

/**
 * Guardrail levels. State is never colour-only: every level pairs a colour with a
 * distinct glyph silhouette and a word. [Unknown] is sync-derived (stale/offline),
 * never percentage-derived — [of] cannot produce it.
 */
enum class Guardrail(
    val label: String,
    @DrawableRes val glyphRes: Int,
) {
    Normal("NORMAL", R.drawable.ic_guardrail_normal),
    Elevated("ELEVATED", R.drawable.ic_guardrail_elevated),
    High("HIGH", R.drawable.ic_guardrail_high),
    Critical("CRITICAL", R.drawable.ic_guardrail_critical),
    Unknown("STALE", R.drawable.ic_guardrail_unknown);

    companion object {
        /** Display band edges — fixed, independent of notification milestones. */
        const val ELEVATED_AT = 50f
        const val HIGH_AT = 75f
        const val CRITICAL_AT = 90f

        fun of(pct: Float): Guardrail = when {
            pct < ELEVATED_AT -> Normal
            pct < HIGH_AT -> Elevated
            pct < CRITICAL_AT -> High
            else -> Critical
        }
    }
}

@Immutable
data class GuardrailColors(
    val fg: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * Semantic colours that live outside [androidx.compose.material3.ColorScheme] so
 * dynamic colour can recolour the chrome without ever recolouring a warning.
 */
@Immutable
data class OpenUsageColors(
    val normal: GuardrailColors,
    val elevated: GuardrailColors,
    val high: GuardrailColors,
    val critical: GuardrailColors,
    val unknown: GuardrailColors,
    val claude: Color,
    val codex: Color,
    val grok: Color,
) {
    fun forLevel(g: Guardrail): GuardrailColors = when (g) {
        Guardrail.Normal -> normal
        Guardrail.Elevated -> elevated
        Guardrail.High -> high
        Guardrail.Critical -> critical
        Guardrail.Unknown -> unknown
    }

    fun accentFor(provider: String): Color = when (provider.lowercase()) {
        "codex" -> codex
        "grok" -> grok
        else -> claude
    }
}

val LightOpenUsageColors = OpenUsageColors(
    normal = GuardrailColors(StatusSafeLight, StatusSafeContainerLight, StatusSafeOnLight),
    elevated = GuardrailColors(StatusModerateLight, StatusModerateContainerLight, StatusModerateOnLight),
    high = GuardrailColors(StatusHighLight, StatusHighContainerLight, Color(0xFF360F00)),
    critical = GuardrailColors(StatusCriticalLight, StatusCriticalContainerLight, StatusCriticalOnLight),
    unknown = GuardrailColors(StatusUnknownLight, StatusUnknownContainerLight, OnSurfaceLight),
    claude = PrimaryLight,
    codex = SecondaryLight,
    grok = GrokAccentLight,
)

val DarkOpenUsageColors = OpenUsageColors(
    normal = GuardrailColors(StatusSafeDark, StatusSafeContainerDark, StatusSafeOnDark),
    elevated = GuardrailColors(StatusModerateDark, StatusModerateContainerDark, StatusModerateOnDark),
    high = GuardrailColors(StatusHighDark, StatusHighContainerDark, Color(0xFFFFDBCA)),
    critical = GuardrailColors(StatusCriticalDark, StatusCriticalContainerDark, StatusCriticalOnDark),
    unknown = GuardrailColors(StatusUnknownDark, StatusUnknownContainerDark, OnSurfaceDark),
    claude = PrimaryDark,
    codex = SecondaryDark,
    grok = GrokAccentDark,
)

val LocalOpenUsageColors = staticCompositionLocalOf { DarkOpenUsageColors }

/** Convenience accessor: colours for a guardrail level. */
@Composable
fun Guardrail.colors(): GuardrailColors = LocalOpenUsageColors.current.forLevel(this)
