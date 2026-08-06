package com.qbapps.claudeusage.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState

/** The independent 4×2 widget represented by option 10a in the design handoff. */
class FourLimitUsageWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(340.dp, 162.dp))
    )
    override val stateDefinition = UsageWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            FourLimitWidgetContent(
                data = currentState<Preferences>().toHeadroomWidgetData()
            )
        }
    }
}
