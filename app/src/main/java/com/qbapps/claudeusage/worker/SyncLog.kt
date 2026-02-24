package com.qbapps.claudeusage.worker

import android.content.Context
import android.util.Log
import com.qbapps.claudeusage.BuildConfig
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Simple file-based logger for debugging background sync.
 * Writes to `files/sync_debug.log` in the app's internal storage.
 *
 * Pull with: adb shell run-as com.qbapps.claudeusage cat files/sync_debug.log
 */
object SyncLog {

    private const val TAG = "UsageSync"
    private const val FILE_NAME = "sync_debug.log"
    private const val MAX_LINES = 200

    private val formatter = DateTimeFormatter
        .ofPattern("MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    fun d(context: Context, message: String) {
        val ts = formatter.format(Instant.now())
        val line = "$ts  $message"
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
        appendToFile(context, line)
    }

    fun getLog(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else "(empty)"
    }

    private fun appendToFile(context: Context, line: String) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            // Trim if too large
            if (file.exists() && file.readLines().size > MAX_LINES) {
                val trimmed = file.readLines().takeLast(MAX_LINES / 2)
                file.writeText(trimmed.joinToString("\n") + "\n")
            }
            file.appendText(line + "\n")
        } catch (_: Exception) {
            // Don't crash for debug logging
        }
    }
}
