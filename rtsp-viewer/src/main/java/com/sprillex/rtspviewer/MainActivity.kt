package com.sprillex.rtspviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var tunnelInitiatedBySession: Boolean = false
    private var tunnelName: String? = "HomeLab"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        teardownTunnelIfNeeded()
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri: Uri? = intent?.data
        if (uri != null && uri.scheme == SCHEME_FASTCAM && uri.host == HOST_VIDEO) {
            val streamOverride = uri.getQueryParameter(PARAM_STREAM)
            if (!streamOverride.isNullOrBlank()) {
                val prefs = getSharedPreferences(ConfigReceiver.PREFS_NAME, MODE_PRIVATE)
                prefs.edit().putString(ConfigReceiver.KEY_STREAM_URI, streamOverride).apply()
            }
        }
    }

    private fun releasePlayer() {
        player?.let {
            it.stop()
            it.clearMediaItems()
            it.release()
            player = null
        }
    }

    private fun teardownTunnelIfNeeded() {
        if (tunnelInitiatedBySession) {
            val intent = Intent(ACTION_SET_TUNNEL_DOWN).apply {
                putExtra(EXTRA_NAME, tunnelName ?: TUNNEL_NAME_DEFAULT)
            }
            sendBroadcast(intent)
            tunnelInitiatedBySession = false
        }
    }

    companion object {
        private const val SCHEME_FASTCAM = "fastcam"
        private const val HOST_VIDEO = "video"
        private const val PARAM_STREAM = "stream"
        private const val ACTION_SET_TUNNEL_DOWN = "com.wireguard.android.action.SET_TUNNEL_DOWN"
        private const val EXTRA_NAME = "name"
        private const val TUNNEL_NAME_DEFAULT = "HomeLab"
    }
}
