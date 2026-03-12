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
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.qbapps.claudeusage.domain.model.ClaudeUsage

private val PillSecondaryText = ColorProvider(day = Color(0xFF6C6F7E), night = Color(0xFFAFA89E))

@Composable
fun PillWidgetContent(usage: ClaudeUsage?, widthDp: Int, heightDp: Int) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionRunCallback<OpenAppActionCallback>()),
        contentAlignment = Alignment.Center,
    ) {
        if (usage == null) {
            Text(
                "Tap to set up",
                style = TextStyle(color = PillSecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
        } else {
            val context = LocalContext.current
            val metric = usage.fiveHour
            val pct = metric?.utilization?.coerceIn(0.0, 100.0) ?: 0.0

            val pillWidth = widthDp.coerceAtLeast(140)
            val pillHeight = ((heightDp - 6) / 2).coerceAtLeast(30)

            val usageBitmap = PillWidgetRenderer.renderUsage(
                context = context,
                percentage = pct,
                status = metric?.status,
                widthDp = pillWidth,
                heightDp = pillHeight,
            )

            val timerBitmap = PillWidgetRenderer.renderTimer(
                context = context,
                resetsAt = metric?.resetsAt,
                widthDp = pillWidth,
                heightDp = pillHeight,
            )

            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    provider = ImageProvider(usageBitmap),
                    contentDescription = "Session ${pct.toInt()}%",
                    modifier = GlanceModifier.fillMaxWidth().height(pillHeight.dp),
                    contentScale = ContentScale.FillBounds,
                )
                Spacer(GlanceModifier.height(4.dp))
                Image(
                    provider = ImageProvider(timerBitmap),
                    contentDescription = "Reset countdown",
                    modifier = GlanceModifier.fillMaxWidth().height(pillHeight.dp),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
    }
}
