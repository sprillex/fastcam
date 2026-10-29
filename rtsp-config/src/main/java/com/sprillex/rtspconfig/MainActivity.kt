package com.sprillex.rtspconfig

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etPath: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSaveSync: Button
    private lateinit var btnForceSync: Button
    private lateinit var tvStatus: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        etPath = findViewById(R.id.etPath)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnSaveSync = findViewById(R.id.btnSaveSync)
        btnForceSync = findViewById(R.id.btnForceSync)
        tvStatus = findViewById(R.id.tvStatus)

        btnSaveSync.setOnClickListener {
            validateAndSync(force = false)
        }

        btnForceSync.setOnClickListener {
            validateAndSync(force = true)
        }
    }

    private fun validateAndSync(force: Boolean = false) {
        val rawHostInput = etHost.text.toString().trim()
        val portStr = etPort.text.toString().trim()
        val pathInput = etPath.text.toString().trim()
        val userInput = etUsername.text.toString().trim()
        val passInput = etPassword.text.toString().trim()

        if (rawHostInput.isEmpty()) {
            etHost.error = "Host or RTSP URL is required"
            return
        }

        var host = rawHostInput
        var port = portStr.toIntOrNull() ?: 554
        var path = pathInput
        var username = userInput
        var password = passInput

        if (rawHostInput.contains("://")) {
            try {
                val parsedUri = Uri.parse(rawHostInput)
                parsedUri.host?.let { host = it }
                if (parsedUri.port != -1) {
                    port = parsedUri.port
                }
                parsedUri.path?.let { if (it.isNotEmpty()) path = it }
                parsedUri.userInfo?.let { userInfo ->
                    val parts = userInfo.split(":", limit = 2)
                    if (parts.isNotEmpty() && parts[0].isNotEmpty()) username = parts[0]
                    if (parts.size > 1) password = parts[1]
                }
            } catch (e: Exception) {
                // Keep raw inputs if URI parsing fails
            }
        }

        val streamUri = RtspUriUtils.buildRtspUri(host, port, path, username, password)
        if (!RtspUriUtils.isValidRtspUri(streamUri)) {
            tvStatus.text = "Error: Invalid RTSP URI generated."
            return
        }

        val maskedUri = RtspUriUtils.maskCredentials(streamUri)

        if (force) {
            sendConfigBroadcast(streamUri)
            tvStatus.text = "Force sync complete!\nBroadcast sent: $maskedUri"
            return
        }

        tvStatus.text = "Testing TCP connection to $host:$port..."
        btnSaveSync.isEnabled = false
        btnForceSync.isEnabled = false

        executor.execute {
            val success = testTcpHandshake(host, port)
            mainHandler.post {
                btnSaveSync.isEnabled = true
                btnForceSync.isEnabled = true
                if (success) {
                    sendConfigBroadcast(streamUri)
                    tvStatus.text = "Connection successful!\nBroadcast sent: $maskedUri"
                } else {
                    tvStatus.text = "TCP handshake failed targeting $host:$port.\nUse 'Force Sync' to send broadcast anyway."
                }
            }
        }
    }

    private fun testTcpHandshake(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
                socket.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun sendConfigBroadcast(streamUri: String) {
        val intent = Intent(ACTION_UPDATE_STREAM).apply {
            setPackage(TARGET_PACKAGE)
            putExtra(EXTRA_STREAM_URI, streamUri)
        }
        sendBroadcast(intent, PERMISSION_WRITE_CONFIG)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    companion object {
        private const val ACTION_UPDATE_STREAM = "com.sprillex.rtspviewer.ACTION_UPDATE_STREAM"
        private const val TARGET_PACKAGE = "com.sprillex.rtspviewer"
        private const val EXTRA_STREAM_URI = "stream_uri"
        private const val PERMISSION_WRITE_CONFIG = "com.sprillex.rtspviewer.permission.WRITE_CONFIG"
    }
}
