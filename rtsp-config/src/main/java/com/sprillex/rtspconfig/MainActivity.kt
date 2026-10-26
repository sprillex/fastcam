package com.sprillex.rtspconfig

import android.content.Intent
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
        tvStatus = findViewById(R.id.tvStatus)

        btnSaveSync.setOnClickListener {
            validateAndSync()
        }
    }

    private fun validateAndSync() {
        val host = etHost.text.toString().trim()
        val portStr = etPort.text.toString().trim()
        val path = etPath.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (host.isEmpty()) {
            etHost.error = "Host is required"
            return
        }

        val port = portStr.toIntOrNull() ?: 554

        tvStatus.text = "Testing TCP connection to $host:$port..."
        btnSaveSync.isEnabled = false

        executor.execute {
            val success = testTcpHandshake(host, port)
            mainHandler.post {
                btnSaveSync.isEnabled = true
                if (success) {
                    val streamUri = buildRtspUri(host, port, path, username, password)
                    sendConfigBroadcast(streamUri)
                    tvStatus.text = "Connection successful!\nBroadcast sent: $streamUri"
                } else {
                    tvStatus.text = "TCP handshake failed targeting $host:$port. Broadcast cancelled."
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

    private fun buildRtspUri(
        host: String,
        port: Int,
        path: String,
        user: String,
        pass: String
    ): String {
        val auth = if (user.isNotEmpty()) {
            if (pass.isNotEmpty()) "$user:$pass@" else "$user@"
        } else {
            ""
        }
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "rtsp://$auth$host:$port$cleanPath"
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
