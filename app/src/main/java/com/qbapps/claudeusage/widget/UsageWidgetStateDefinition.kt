package com.qbapps.claudeusage.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.glance.state.GlanceStateDefinition
import java.io.File

/**
 * Custom [GlanceStateDefinition] for the usage widget.
 *
 * This uses its own separate DataStore file (`widget_usage_cache.preferences_pb`)
 * to avoid the "multiple DataStores active for the same file" crash that
 * occurs when both the app's [UsageDataStore] and the widget try to open
 * the same file concurrently.
 *
 * Data is pushed into this DataStore by the [UsageSyncWorker] after each
 * successful fetch using [updateWidgetState].
 */
object UsageWidgetStateDefinition : GlanceStateDefinition<Preferences> {

    private const val FILE_NAME = "widget_usage_cache.preferences_pb"

    @Volatile
    private var instance: DataStore<Preferences>? = null
    private val lock = Any()

    override suspend fun getDataStore(
        context: Context,
        fileKey: String
    ): DataStore<Preferences> {
        return instance ?: synchronized(lock) {
            instance ?: PreferenceDataStoreFactory.create {
                File(context.applicationContext.filesDir, "datastore/$FILE_NAME")
            }.also { instance = it }
        }
    }

    override fun getLocation(
        context: Context,
        fileKey: String
    ): File {
        return File(context.applicationContext.filesDir, "datastore/$FILE_NAME")
    }
}
