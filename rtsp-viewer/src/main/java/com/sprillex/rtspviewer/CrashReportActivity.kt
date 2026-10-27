package com.sprillex.rtspviewer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class CrashReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_report)

        val crashReport = intent.getStringExtra(AppCrashHandler.EXTRA_CRASH_REPORT)
            ?: run {
                val file = File(filesDir, AppCrashHandler.CRASH_REPORT_FILE)
                if (file.exists()) file.readText() else "No crash report available."
            }

        val tvCrashReport = findViewById<TextView>(R.id.tvCrashReport)
        val btnShare = findViewById<Button>(R.id.btnShareCrashReport)

        tvCrashReport.text = crashReport

        btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, crashReport)
                putExtra("EXTRA_PACKAGE_NAME", packageName)
                putExtra("EXTRA_STACK_TRACE", crashReport)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Crash Report"))
        }
    }
}
