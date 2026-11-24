package com.aiec.elevatoros.data

import android.content.Context
import com.aiec.elevatoros.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * If the app ever closes by mistake, the error is written to app-private storage and shown at the next start with a
 * Share button, so the owner can send it to the developer. It holds only the error and the app/phone version —
 * no names, phone numbers or company data.
 */
object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")

    fun install(c: Context) {
        val app = c.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write(app, "CRASH", e) }
            previous?.uncaughtException(t, e)
        }
    }

    /** An error that was caught (the app stayed open) but should still be reported. */
    fun note(c: Context, e: Throwable) { runCatching { write(c.applicationContext, "HANDLED", e) } }

    private fun write(c: Context, kind: String, e: Throwable) {
        val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
        file(c).writeText(
            "$kind ${Instant.now()}\nAIEC Elevator OS ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}\n" +
                "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n\n" +
                sw.toString().take(20_000)
        )
    }

    fun read(c: Context): String? = file(c).takeIf { it.exists() }?.readText()
    fun clear(c: Context) { file(c).delete() }
}
