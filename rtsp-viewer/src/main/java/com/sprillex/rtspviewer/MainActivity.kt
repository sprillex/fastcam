package com.sprillex.rtspviewer

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var tunnelInitiatedBySession: Boolean = false
    private var tunnelName: String? = "HomeLab"

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ConfigReceiver.KEY_STREAM_URI) {
            loadAndStartStream()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)

        val prefs = getSharedPreferences(ConfigReceiver.PREFS_NAME, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        handleDeepLink(intent)
        initializePlayer()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
        loadAndStartStream()
    }

    override fun onStart() {
        super.onStart()
        if (player == null) {
            initializePlayer()
        } else {
            loadAndStartStream()
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroy() {
        val prefs = getSharedPreferences(ConfigReceiver.PREFS_NAME, MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onDestroy()
        releasePlayer()
        teardownTunnelIfNeeded()
    }

    private fun initializePlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                200, // minBufferMs
                500, // maxBufferMs
                100, // bufferForPlaybackMs
                200  // bufferForPlaybackAfterRebufferMs
            )
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()

        player = exoPlayer
        playerView?.player = exoPlayer

        loadAndStartStream()
    }

    private fun loadAndStartStream() {
        val exoPlayer = player ?: return
        val prefs = getSharedPreferences(ConfigReceiver.PREFS_NAME, MODE_PRIVATE)
        val streamUriString = prefs.getString(ConfigReceiver.KEY_STREAM_URI, null)

        if (!streamUriString.isNullOrBlank()) {
            val mediaItem = MediaItem.fromUri(streamUriString)
            val mediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(mediaItem)

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
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
