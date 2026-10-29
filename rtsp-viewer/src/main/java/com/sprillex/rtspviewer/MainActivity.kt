package com.sprillex.rtspviewer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class MainActivity : AppCompatActivity() {

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var isViewsAttached = false

    private var tunnelInitiatedBySession: Boolean = false
    private var tunnelName: String? = "HomeLab"

    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null
    private var isBuffering = false

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val bufferingTimeoutRunnable = Runnable {
        if (isBuffering) {
            Log.w(TAG, "RTSP connection timed out while buffering. Retrying stream...")
            isBuffering = false
            handleError()
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

        videoLayout = findViewById(R.id.videoLayout)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val prefs = getSharedPreferences(ConfigReceiver.PREFS_NAME, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        handleDeepLink(intent)
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
        registerNetworkCallback()
        checkNetworkAndStartTunnelIfNeeded()
        if (libVLC == null || mediaPlayer == null) {
            initializePlayer()
        } else if (!isViewsAttached) {
            attachPlayerViews()
        }
        loadAndStartStream()
    }

    override fun onStop() {
        super.onStop()
        unregisterNetworkCallback()
        cancelRetry()
        detachPlayerViews()
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

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available. Checking stream status...")
                checkNetworkAndStartTunnelIfNeeded()
                mainHandlerPost {
                    if (mediaPlayer?.isPlaying == false && retryCount < MAX_RETRY_ATTEMPTS) {
                        loadAndStartStream()
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Network lost.")
            }
        }

        try {
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering network callback: ${e.message}")
            }
            networkCallback = null
        }
    }

    private fun mainHandlerPost(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post(action)
        }
    }

    private fun checkNetworkAndStartTunnelIfNeeded() {
        val cm = connectivityManager ?: return
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)

        val isWifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

        if (!isWifi && caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            if (!tunnelInitiatedBySession) {
                Log.i(TAG, "Non-Wi-Fi network detected. Triggering WireGuard VPN tunnel '$tunnelName'...")
                val intent = Intent(ACTION_SET_TUNNEL_UP).apply {
                    putExtra(EXTRA_NAME, tunnelName ?: TUNNEL_NAME_DEFAULT)
                }
                sendBroadcast(intent)
                tunnelInitiatedBySession = true
            }
        }
    }

    private fun initializePlayer() {
        try {
            val options = ArrayList<String>().apply {
                add("--no-audio")
                add("--network-caching=150")
                add("--drop-late-frames")
                add("--skip-frames")
                add("--rtsp-tcp")
            }

            val vlcInstance = LibVLC(this, options)
            val player = MediaPlayer(vlcInstance)

            player.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Buffering -> {
                        val floatBuffer = event.buffering
                        Log.d(TAG, "MediaPlayer Buffering: $floatBuffer")
                        if (floatBuffer < 100f) {
                            if (!isBuffering) {
                                isBuffering = true
                                handler.removeCallbacks(bufferingTimeoutRunnable)
                                handler.postDelayed(bufferingTimeoutRunnable, BUFFERING_TIMEOUT_MS)
                            }
                        } else {
                            isBuffering = false
                            handler.removeCallbacks(bufferingTimeoutRunnable)
                        }
                    }
                    MediaPlayer.Event.Playing -> {
                        Log.d(TAG, "MediaPlayer Playing")
                        isBuffering = false
                        handler.removeCallbacks(bufferingTimeoutRunnable)
                        retryCount = 0
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        Log.e(TAG, "MediaPlayer EncounteredError")
                        isBuffering = false
                        handler.removeCallbacks(bufferingTimeoutRunnable)
                        handleError()
                    }
                    MediaPlayer.Event.EndReached -> {
                        Log.d(TAG, "MediaPlayer EndReached")
                        isBuffering = false
                        handler.removeCallbacks(bufferingTimeoutRunnable)
                    }
                }
            }

            libVLC = vlcInstance
            mediaPlayer = player

            attachPlayerViews()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize LibVLC", t)
            Toast.makeText(this, "Failed to initialize video engine: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun attachPlayerViews() {
        val layout = videoLayout ?: return
        val player = mediaPlayer ?: return
        if (!isViewsAttached) {
            player.attachViews(layout, null, false, false)
            isViewsAttached = true
        }
    }

    private fun detachPlayerViews() {
        val player = mediaPlayer ?: return
        if (isViewsAttached) {
            try {
                player.detachViews()
            } catch (e: Exception) {
                Log.w(TAG, "Error detaching player views: ${e.message}")
            }
            isViewsAttached = false
        }
    }

    private fun handleError() {
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            retryCount++
            val backoffDelayMs = (BASE_RETRY_INTERVAL_MS * (1 shl (retryCount - 1))).coerceAtMost(MAX_RETRY_INTERVAL_MS)
            Log.w(TAG, "RTSP connection failed. Reconnection attempt $retryCount/$MAX_RETRY_ATTEMPTS in ${backoffDelayMs}ms...")
            cancelRetry()
            retryRunnable = Runnable {
                loadAndStartStream()
            }
            handler.postDelayed(retryRunnable!!, backoffDelayMs)
        } else {
            Log.e(TAG, "Stream connection failed after $MAX_RETRY_ATTEMPTS attempts.")
            Toast.makeText(
                this@MainActivity,
                "Stream connection failed after $MAX_RETRY_ATTEMPTS attempts.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun loadAndStartStream() {
        val player = mediaPlayer ?: return
        val vlc = libVLC ?: return

        val prefs = getSharedPreferences(ConfigReceiver.PREFS_NAME, MODE_PRIVATE)
        val streamUriString = prefs.getString(ConfigReceiver.KEY_STREAM_URI, null)
            ?: DEFAULT_RTSP_URI

        val maskedUri = RtspUriUtils.maskCredentials(streamUriString)
        Log.d(TAG, "Loading stream URI: $maskedUri")

        if (RtspUriUtils.isValidRtspUri(streamUriString)) {
            if (player.isPlaying) {
                player.stop()
            }
            val media = Media(vlc, Uri.parse(streamUriString))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":no-audio")
            media.addOption(":network-caching=150")
            media.addOption(":live-caching=150")
            media.addOption(":clock-jitter=0")
            media.addOption(":clock-synchro=0")

            player.media = media
            media.release()
            player.play()
        } else {
            if (player.isPlaying) {
                player.stop()
            }
            Toast.makeText(
                this,
                "No valid stream URI configured. Set stream URI via FastCam Config.",
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
            if (!streamOverride.isNullOrBlank() && RtspUriUtils.isValidRtspUri(streamOverride)) {
                val prefs = getSharedPreferences(ConfigReceiver.PREFS_NAME, MODE_PRIVATE)
                prefs.edit().putString(ConfigReceiver.KEY_STREAM_URI, streamOverride.trim()).apply()
            } else if (!streamOverride.isNullOrBlank()) {
                Log.w(TAG, "Invalid deep link stream URI provided: ${RtspUriUtils.maskCredentials(streamOverride)}")
            }
        }
    }

    private fun releasePlayer() {
        handler.removeCallbacks(bufferingTimeoutRunnable)
        detachPlayerViews()
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
            mediaPlayer = null
        }
        libVLC?.let {
            it.release()
            libVLC = null
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
        private const val BASE_RETRY_INTERVAL_MS = 500L
        private const val MAX_RETRY_INTERVAL_MS = 5000L
        private const val BUFFERING_TIMEOUT_MS = 6000L
    }
}
