package com.qbapps.claudeusage.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.appwidget.action.actionRunCallback
import androidx.datastore.preferences.core.Preferences

class UsagePillWidget : GlanceAppWidget() {

    companion object {
        private val PILL = DpSize(140.dp, 52.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(PILL))

    override val stateDefinition = UsageWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val usage = prefs.toClaudeUsage()
            val size = LocalSize.current
            PillWidgetContent(usage = usage, widthDp = size.width.value.toInt(), heightDp = size.height.value.toInt())
        }
    }
}
