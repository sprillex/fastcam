package com.sprillex.rtspviewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ConfigReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != ACTION_UPDATE_STREAM) {
            return
        }

        val streamUri = intent.getStringExtra(EXTRA_STREAM_URI) ?: return
        if (streamUri.isNotBlank()) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_STREAM_URI, streamUri).apply()
        }
    }

    companion object {
        const val ACTION_UPDATE_STREAM = "com.sprillex.rtspviewer.ACTION_UPDATE_STREAM"
        const val EXTRA_STREAM_URI = "stream_uri"
        const val PREFS_NAME = "viewer_config"
        const val KEY_STREAM_URI = "stream_uri"
    }
}
