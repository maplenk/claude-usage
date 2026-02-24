package com.qbapps.claudeusage.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.qbapps.claudeusage.domain.model.ClaudeUsage
import java.time.Duration
import java.time.Instant

enum class WidgetSize {
    SMALL,  // 1x1: ring only
    MEDIUM, // 2x1: ring + text side by side
    LARGE   // 2x2: ring + countdown + refresh
}

private val Surface = ColorProvider(day = Color(0xFFF4F6FB), night = Color(0xFF14161B))
private val PrimaryText = ColorProvider(day = Color(0xFF1B1B1F), night = Color(0xFFE4E1E6))
private val SecondaryText = ColorProvider(day = Color(0xFF46464F), night = Color(0xFFC7C5D0))
private val TertiaryText = ColorProvider(day = Color(0xFF6A6A74), night = Color(0xFFA8A6B1))
private val RefreshBtnBg = ColorProvider(day = Color(0xFFE8ECF6), night = Color(0xFF2C303A))

@Composable
fun UsageWidgetContent(usage: ClaudeUsage?, widgetSize: WidgetSize) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Surface)
            .clickable(actionRunCallback<OpenAppActionCallback>()),
        contentAlignment = Alignment.Center
    ) {
        if (usage == null) {
            EmptyState(widgetSize)
        } else {
            when (widgetSize) {
                WidgetSize.SMALL -> SmallLayout(usage)
                WidgetSize.MEDIUM -> MediumLayout(usage)
                WidgetSize.LARGE -> LargeLayout(usage)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState(widgetSize: WidgetSize) {
    if (widgetSize == WidgetSize.SMALL) {
        Text("C", style = TextStyle(color = SecondaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold))
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Claude", style = TextStyle(color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(2.dp))
            Text("Tap to set up", style = TextStyle(color = SecondaryText, fontSize = 10.sp))
        }
    }
}

// ---------------------------------------------------------------------------
// 1x1: just the ring, nothing else
// ---------------------------------------------------------------------------

@Composable
private fun SmallLayout(usage: ClaudeUsage) {
    val context = LocalContext.current
    val metric = usage.fiveHour
    val pct = metric?.utilization?.coerceIn(0.0, 100.0) ?: 0.0

    val bitmap = WidgetRingRenderer.render(context, pct, metric?.status, ringDp = 54)

    Image(
        provider = ImageProvider(bitmap),
        contentDescription = "Session ${pct.toInt()}%",
        modifier = GlanceModifier.size(54.dp),
        contentScale = ContentScale.Fit
    )
}

// ---------------------------------------------------------------------------
// 2x1: ring on left, text on right
// ---------------------------------------------------------------------------

@Composable
private fun MediumLayout(usage: ClaudeUsage) {
    val context = LocalContext.current
    val metric = usage.fiveHour
    val pct = metric?.utilization?.coerceIn(0.0, 100.0) ?: 0.0

    val bitmap = WidgetRingRenderer.render(context, pct, metric?.status, ringDp = 48)

    Row(
        modifier = GlanceModifier.fillMaxSize().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "Session ${pct.toInt()}%",
            modifier = GlanceModifier.size(48.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(GlanceModifier.width(10.dp))
        Column {
            Text(
                "Session",
                style = TextStyle(color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            )
            metric?.resetsAt?.let {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    formatCountdown(it),
                    style = TextStyle(color = TertiaryText, fontSize = 10.sp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2x2: ring centered, countdown + refresh at bottom
// ---------------------------------------------------------------------------

@Composable
private fun LargeLayout(usage: ClaudeUsage) {
    val context = LocalContext.current
    val metric = usage.fiveHour
    val pct = metric?.utilization?.coerceIn(0.0, 100.0) ?: 0.0

    val bitmap = WidgetRingRenderer.render(context, pct, metric?.status, ringDp = 100)

    Column(
        modifier = GlanceModifier.fillMaxSize().padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "Session ${pct.toInt()}%",
            modifier = GlanceModifier.size(100.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(GlanceModifier.height(8.dp))

        // Bottom row: countdown left, refresh right
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = metric?.resetsAt?.let { formatCountdown(it) } ?: "",
                style = TextStyle(color = TertiaryText, fontSize = 10.sp)
            )
            Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = GlanceModifier
                        .size(26.dp)
                        .cornerRadius(13.dp)
                        .background(RefreshBtnBg)
                        .clickable(actionRunCallback<RefreshActionCallback>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\u21BB", style = TextStyle(color = SecondaryText, fontSize = 13.sp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun formatCountdown(resetsAt: Instant): String {
    val remaining = Duration.between(Instant.now(), resetsAt)
    if (remaining.isNegative || remaining.isZero) return "Ready"
    val hours = remaining.toHours()
    val minutes = remaining.toMinutes() % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
