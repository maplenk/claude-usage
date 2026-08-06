package com.qbapps.claudeusage.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState

/** The independent 4×2 widget represented by option 10a in the design handoff. */
class FourLimitUsageWidget : GlanceAppWidget() {

    // Render against the launcher's actual bounds. A single responsive size would
    // preserve a 340×162 RemoteViews snapshot inside larger launcher allocations.
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition = UsageWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            FourLimitWidgetContent(
                data = currentState<Preferences>().toHeadroomWidgetData()
            )
        }
    }
}
