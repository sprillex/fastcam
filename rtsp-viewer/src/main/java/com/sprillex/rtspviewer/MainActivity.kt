package com.sprillex.rtspviewer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var tunnelInitiatedBySession: Boolean = false
    private var tunnelName: String? = "HomeLab"

    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null

    private val bufferingTimeoutRunnable = Runnable {
        if (player?.playbackState == Player.STATE_BUFFERING) {
            Log.w(TAG, "RTSP connection timed out while buffering. Retrying stream...")
            loadAndStartStream()
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ConfigReceiver.KEY_STREAM_URI) {
            retryCount = 0
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
        // checkNetworkAndStartTunnelIfNeeded()
        initializePlayer()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
        retryCount = 0
        loadAndStartStream()
    }

    override fun onStart() {
        super.onStart()
        if (player == null) {
            initializePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        cancelRetry()
        releasePlayer()
    }

    override fun onDestroy() {
        val prefs = getSharedPreferences(ConfigReceiver.PREFS_NAME, MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        cancelRetry()
        super.onDestroy()
        releasePlayer()
        teardownTunnelIfNeeded()
    }

    private fun checkNetworkAndStartTunnelIfNeeded() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)

        val isWifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

        if (!isWifi) {
            val intent = Intent(ACTION_SET_TUNNEL_UP).apply {
                putExtra(EXTRA_NAME, tunnelName ?: TUNNEL_NAME_DEFAULT)
            }
            sendBroadcast(intent)
            tunnelInitiatedBySession = true
        }
    }

    private fun initializePlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2500, // minBufferMs
                5000, // maxBufferMs
                2000, // bufferForPlaybackMs
                2000  // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()

        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()

        exoPlayer.addAnalyticsListener(EventLogger())

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateString = when (playbackState) {
                    Player.STATE_IDLE -> "STATE_IDLE"
                    Player.STATE_BUFFERING -> "STATE_BUFFERING"
                    Player.STATE_READY -> "STATE_READY"
                    Player.STATE_ENDED -> "STATE_ENDED"
                    else -> "UNKNOWN"
                }
                Log.d(TAG, "onPlaybackStateChanged: $stateString ($playbackState)")

                handler.removeCallbacks(bufferingTimeoutRunnable)
                if (playbackState == Player.STATE_BUFFERING) {
                    handler.postDelayed(bufferingTimeoutRunnable, BUFFERING_TIMEOUT_MS)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer error code: ${error.errorCodeName} (${error.errorCode})", error)
                error.cause?.let { Log.e(TAG, "Root cause: ${it.message}", it) }
                if (retryCount < MAX_RETRY_ATTEMPTS) {
                    retryCount++
                    cancelRetry()
                    retryRunnable = Runnable {
                        loadAndStartStream()
                    }
                    handler.postDelayed(retryRunnable!!, RETRY_INTERVAL_MS)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Stream connection failed: ${error.localizedMessage ?: "Playback error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })

        player = exoPlayer
        playerView?.player = exoPlayer

        loadAndStartStream()
    }

    private fun loadAndStartStream() {
        val exoPlayer = player ?: return
        val prefs = getSharedPreferences(ConfigReceiver.PREFS_NAME, MODE_PRIVATE)
        val streamUriString = prefs.getString(ConfigReceiver.KEY_STREAM_URI, null)
            ?: DEFAULT_RTSP_URI

        Log.d(TAG, "Loading stream URI: $streamUriString")

        if (!streamUriString.isNullOrBlank()) {
            val mediaItem = MediaItem.fromUri(streamUriString)
            val mediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(false)
                .setDebugLoggingEnabled(true)
                .createMediaSource(mediaItem)

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            Toast.makeText(
                this,
                "No stream URI configured. Set stream URI via FastCam Config.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun cancelRetry() {
        handler.removeCallbacks(bufferingTimeoutRunnable)
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
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
        handler.removeCallbacks(bufferingTimeoutRunnable)
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
        private const val TAG = "MainActivity"
        private const val DEFAULT_RTSP_URI = "rtsp://admin:password@192.168.1.202:554/video1"
        private const val SCHEME_FASTCAM = "fastcam"
        private const val HOST_VIDEO = "video"
        private const val PARAM_STREAM = "stream"
        private const val ACTION_SET_TUNNEL_UP = "com.wireguard.android.action.SET_TUNNEL_UP"
        private const val ACTION_SET_TUNNEL_DOWN = "com.wireguard.android.action.SET_TUNNEL_DOWN"
        private const val EXTRA_NAME = "name"
        private const val TUNNEL_NAME_DEFAULT = "HomeLab"
        private const val MAX_RETRY_ATTEMPTS = 4
        private const val RETRY_INTERVAL_MS = 150L
        private const val BUFFERING_TIMEOUT_MS = 6000L
    }
}
