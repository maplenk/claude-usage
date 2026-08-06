package com.qbapps.claudeusage.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Radius encodes rank: 28dp = a card you act on (hero, provider), 20dp = reference
 * tile, 12dp = input, full = chip or button. Grouped rows use asymmetric corners so
 * a stack reads as one object.
 */
object OpenUsageShape {
    val tile = RoundedCornerShape(20.dp)
    val card = RoundedCornerShape(28.dp)
    val groupTop = RoundedCornerShape(20.dp, 20.dp, 6.dp, 6.dp)
    val groupMid = RoundedCornerShape(6.dp)
    val groupEnd = RoundedCornerShape(6.dp, 6.dp, 20.dp, 20.dp)
}

/** 4dp-base spacing scale. */
object OpenUsageSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val base = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val section = 40.dp
}
