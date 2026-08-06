package com.qbapps.claudeusage.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

enum class UsageIndicatorStyle {
    FLAT,
    WAVY,
    STALE,
}

/**
 * Shared usage indicator for every app card.
 *
 * Threshold gaps make the 50/75/90 bands readable without relying on colour.
 * Stale state deliberately loses saturation and becomes segmented.
 */
@Composable
fun UsageIndicator(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    style: UsageIndicatorStyle = UsageIndicatorStyle.FLAT,
    height: Dp = if (style == UsageIndicatorStyle.WAVY) 12.dp else 10.dp,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650),
        label = "usage_indicator_progress",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val separatorColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val renderedColor = if (style == UsageIndicatorStyle.STALE) {
        color.copy(alpha = 0.62f)
    } else {
        color
    }

    Canvas(
        modifier = modifier.progressSemantics(animatedProgress),
    ) {
        val indicatorHeight = height.toPx().coerceAtMost(size.height)
        val centerY = size.height / 2f
        val top = centerY - indicatorHeight / 2f
        val progressWidth = size.width * animatedProgress

        when (style) {
            UsageIndicatorStyle.FLAT -> {
                val radius = indicatorHeight / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, indicatorHeight),
                    cornerRadius = CornerRadius(radius, radius),
                )
                if (progressWidth > 0f) {
                    drawRoundRect(
                        color = renderedColor,
                        topLeft = Offset(0f, top),
                        size = Size(progressWidth, indicatorHeight),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
            }

            UsageIndicatorStyle.WAVY -> {
                val amplitude = 2.5.dp.toPx().coerceAtMost(indicatorHeight / 3f)
                val wavelength = 18.dp.toPx()
                val strokeWidth = 5.dp.toPx().coerceAtMost(indicatorHeight)

                fun wavePath(endX: Float): Path = Path().apply {
                    var x = 0f
                    moveTo(0f, centerY)
                    while (x <= endX) {
                        val y = centerY +
                            sin((x / wavelength) * 2.0 * PI).toFloat() * amplitude
                        lineTo(x, y)
                        x += 2.dp.toPx()
                    }
                }

                drawPath(
                    path = wavePath(size.width),
                    color = trackColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                if (progressWidth > 0f) {
                    drawPath(
                        path = wavePath(progressWidth),
                        color = renderedColor,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }

            UsageIndicatorStyle.STALE -> {
                val dashWidth = 7.dp.toPx()
                val dashGap = 4.dp.toPx()
                val strokeWidth = 5.dp.toPx().coerceAtMost(indicatorHeight)
                var x = 0f
                while (x < size.width) {
                    val end = (x + dashWidth).coerceAtMost(size.width)
                    drawLine(
                        color = trackColor,
                        start = Offset(x, centerY),
                        end = Offset(end, centerY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    if (x < progressWidth) {
                        drawLine(
                            color = renderedColor,
                            start = Offset(x, centerY),
                            end = Offset(end.coerceAtMost(progressWidth), centerY),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                    x += dashWidth + dashGap
                }
            }
        }

        listOf(0.50f, 0.75f, 0.90f).forEach { threshold ->
            val x = size.width * threshold
            drawLine(
                color = separatorColor,
                start = Offset(x, top - 1.dp.toPx()),
                end = Offset(x, top + indicatorHeight + 1.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
            )
        }

        if (animatedProgress > 0f && style != UsageIndicatorStyle.STALE) {
            val stopRadius = 3.dp.toPx()
            drawCircle(
                color = renderedColor,
                radius = stopRadius,
                center = Offset(
                    progressWidth.coerceIn(
                        stopRadius,
                        (size.width - stopRadius).coerceAtLeast(stopRadius),
                    ),
                    centerY,
                ),
            )
        }
    }
}
