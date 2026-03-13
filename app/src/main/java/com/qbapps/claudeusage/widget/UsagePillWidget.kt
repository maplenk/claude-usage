package com.qbapps.claudeusage.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.datastore.preferences.core.Preferences

class UsagePillWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT = DpSize(120.dp, 57.dp)
        private val ROOMY = DpSize(128.dp, 64.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, ROOMY))

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
