package com.sprillex.rtspviewer

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class AppCrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val report = generateCrashReport(context, thread, throwable)
            saveCrashReport(context, report)
            launchCrashReportActivity(context, report)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling uncaught exception", e)
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "AppCrashHandler"
        const val CRASH_REPORT_FILE = "latest_crash_report.txt"
        const val EXTRA_CRASH_REPORT = "extra_crash_report"

        fun install(context: Context) {
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (currentHandler !is AppCrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(
                    AppCrashHandler(context.applicationContext, currentHandler)
                )
            }
        }

        fun generateCrashReport(context: Context, thread: Thread, throwable: Throwable): String {
            val packageName = context.packageName
            val pInfo = try {
                context.packageManager.getPackageInfo(packageName, 0)
            } catch (e: Exception) {
                null
            }
            val versionName = pInfo?.versionName ?: "1.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo?.longVersionCode ?: 1
            } else {
                @Suppress("DEPRECATION")
                pInfo?.versionCode ?: 1
            }

            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            return """
                === APP CRASH REPORT ===
                Package: $packageName
                Version: $versionName ($versionCode)
                Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT}, Android ${Build.VERSION.RELEASE})
                Thread: ${thread.name}

                === STACK TRACE ===
                $stackTrace
            """.trimIndent()
        }

        private fun saveCrashReport(context: Context, report: String) {
            try {
                val file = File(context.filesDir, CRASH_REPORT_FILE)
                file.writeText(report)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save crash report to storage", e)
            }
        }

        private fun launchCrashReportActivity(context: Context, report: String) {
            val intent = Intent(context, CrashReportActivity::class.java).apply {
                putExtra(EXTRA_CRASH_REPORT, report)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }
}
