package dev.flowpilot.app.data

import dev.flowpilot.core.sync.catching

import android.content.Context
import android.os.Build
import android.util.Log
import dev.flowpilot.app.BuildConfig
import java.io.File

/**
 * Keeps the stack trace of the last crash on the phone, so the next launch can show it and it can be copied
 * into a bug report. Nothing leaves the device.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            catching {
                file(app).writeText(
                    buildString {
                        appendLine("FlowPilot ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("Thread: ${thread.name}")
                        appendLine()
                        append(Log.getStackTraceString(error))
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = catching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) { catching { file(context).delete() } }
}
