package com.sprillex.rtspviewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ConfigReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != ACTION_UPDATE_STREAM) {
            return
        }

        val streamUri = intent.getStringExtra(EXTRA_STREAM_URI) ?: return
        if (RtspUriUtils.isValidRtspUri(streamUri)) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_STREAM_URI, streamUri.trim()).apply()
            Log.i(TAG, "Stream URI updated securely: ${RtspUriUtils.maskCredentials(streamUri)}")
        } else {
            Log.w(TAG, "Received invalid RTSP URI in broadcast update attempt")
        }
    }

    companion object {
        private const val TAG = "ConfigReceiver"
        const val ACTION_UPDATE_STREAM = "com.sprillex.rtspviewer.ACTION_UPDATE_STREAM"
        const val EXTRA_STREAM_URI = "stream_uri"
        const val PREFS_NAME = "viewer_config"
        const val KEY_STREAM_URI = "stream_uri"
    }
}
