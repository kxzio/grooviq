package com.example.groviq

import android.app.Application
import android.content.Context
import androidx.media3.common.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

fun appendLog(
    context: Context,
    tag: String,
    message: String,
    throwable: Throwable? = null
) {
    try {
        val logDir = context.getExternalFilesDir(null)
        if (logDir == null) {
            return
        }

        val logFile = File(logDir, "music_app_errors.log")
        if (!logFile.exists()) logFile.createNewFile()

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())

        val logBuilder = StringBuilder().apply {
            append("$timestamp [$tag] $message\n")
            throwable?.let {
                val sw = StringWriter()
                it.printStackTrace(PrintWriter(sw))
                append(sw.toString()).append("\n")
            }
        }

        logFile.appendText(logBuilder.toString())


    } catch (e: Exception) {

    }
}