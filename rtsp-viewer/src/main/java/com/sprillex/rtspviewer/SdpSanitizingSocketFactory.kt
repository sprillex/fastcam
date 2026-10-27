package com.sprillex.rtspviewer

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.channels.SocketChannel
import javax.net.SocketFactory

class SdpSanitizingSocketFactory(
    private val delegate: SocketFactory = getDefault()
) : SocketFactory() {

    override fun createSocket(): Socket = SdpSanitizingSocket(delegate.createSocket())
    override fun createSocket(host: String?, port: Int): Socket = SdpSanitizingSocket(delegate.createSocket(host, port))
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = SdpSanitizingSocket(delegate.createSocket(host, port, localHost, localPort))
    override fun createSocket(host: InetAddress?, port: Int): Socket = SdpSanitizingSocket(delegate.createSocket(host, port))
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = SdpSanitizingSocket(delegate.createSocket(address, port, localAddress, localPort))
}

class SdpSanitizingSocket(private val delegate: Socket) : Socket() {
    private var inputStreamWrapper: InputStream? = null

    override fun connect(endpoint: SocketAddress?) = delegate.connect(endpoint)
    override fun connect(endpoint: SocketAddress?, timeout: Int) = delegate.connect(endpoint, timeout)
    override fun bind(bindpoint: SocketAddress?) = delegate.bind(bindpoint)
    override fun getInetAddress(): InetAddress? = delegate.inetAddress
    override fun getLocalAddress(): InetAddress = delegate.localAddress
    override fun getPort(): Int = delegate.port
    override fun getLocalPort(): Int = delegate.localPort
    override fun getRemoteSocketAddress(): SocketAddress? = delegate.remoteSocketAddress
    override fun getLocalSocketAddress(): SocketAddress? = delegate.localSocketAddress
    override fun getOutputStream(): OutputStream = delegate.outputStream
    override fun close() = delegate.close()
    override fun isConnected(): Boolean = delegate.isConnected
    override fun isBound(): Boolean = delegate.isBound
    override fun isClosed(): Boolean = delegate.isClosed

    override fun setSoTimeout(timeout: Int) { delegate.soTimeout = timeout }
    override fun getSoTimeout(): Int = delegate.soTimeout
    override fun setTcpNoDelay(on: Boolean) { delegate.tcpNoDelay = on }
    override fun getTcpNoDelay(): Boolean = delegate.tcpNoDelay
    override fun setKeepAlive(on: Boolean) { delegate.keepAlive = on }
    override fun getKeepAlive(): Boolean = delegate.keepAlive
    override fun setReceiveBufferSize(size: Int) { delegate.receiveBufferSize = size }
    override fun getReceiveBufferSize(): Int = delegate.receiveBufferSize
    override fun setSendBufferSize(size: Int) { delegate.sendBufferSize = size }
    override fun getSendBufferSize(): Int = delegate.sendBufferSize
    override fun setSoLinger(on: Boolean, linger: Int) { delegate.setSoLinger(on, linger) }
    override fun getSoLinger(): Int = delegate.soLinger
    override fun shutdownInput() = delegate.shutdownInput()
    override fun shutdownOutput() = delegate.shutdownOutput()
    override fun isInputShutdown(): Boolean = delegate.isInputShutdown
    override fun isOutputShutdown(): Boolean = delegate.isOutputShutdown
    override fun getChannel(): SocketChannel? = delegate.channel

    override fun getInputStream(): InputStream {
        if (inputStreamWrapper == null) {
            inputStreamWrapper = SdpSanitizingInputStream(delegate.inputStream)
        }
        return inputStreamWrapper!!
    }
}

class SdpSanitizingInputStream(private val delegate: InputStream) : InputStream() {
    private var bufferedData: ByteArrayInputStream? = null
    private var hasSanitizedSdp = false

    override fun read(): Int {
        if (hasSanitizedSdp && bufferedData == null) {
            return delegate.read()
        }
        ensureDataAvailable()
        if (bufferedData != null) {
            val b = bufferedData!!.read()
            if (bufferedData!!.available() <= 0) {
                bufferedData = null
            }
            return b
        }
        return delegate.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (hasSanitizedSdp && bufferedData == null) {
            return delegate.read(b, off, len)
        }
        ensureDataAvailable()
        if (bufferedData != null) {
            val count = bufferedData!!.read(b, off, len)
            if (bufferedData!!.available() <= 0) {
                bufferedData = null
            }
            if (count > 0) {
                return count
            }
        }
        return delegate.read(b, off, len)
    }

    private fun ensureDataAvailable() {
        if (bufferedData != null || hasSanitizedSdp) {
            return
        }

        val headerBaos = ByteArrayOutputStream()
        val temp = ByteArray(1)
        val lineBuffer = ByteArray(4)
        var lineIndex = 0

        while (true) {
            val read = delegate.read(temp)
            if (read <= 0) break
            val b = temp[0]
            headerBaos.write(b.toInt())

            if (lineIndex < 4) {
                lineBuffer[lineIndex++] = b
            } else {
                lineBuffer[0] = lineBuffer[1]
                lineBuffer[1] = lineBuffer[2]
                lineBuffer[2] = lineBuffer[3]
                lineBuffer[3] = b
            }

            if (lineIndex == 4 &&
                lineBuffer[0] == '\r'.code.toByte() &&
                lineBuffer[1] == '\n'.code.toByte() &&
                lineBuffer[2] == '\r'.code.toByte() &&
                lineBuffer[3] == '\n'.code.toByte()
            ) {
                break
            }
        }

        val headerText = String(headerBaos.toByteArray(), Charsets.UTF_8)
        if (headerText.startsWith("RTSP/", ignoreCase = true) && headerText.contains("application/sdp", ignoreCase = true)) {
            val contentLengthMatch = Regex("""Content-[Ll]ength:\s*(\d+)""", RegexOption.IGNORE_CASE).find(headerText)
            val contentLength = contentLengthMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val bodyBytes = ByteArray(contentLength)
            var bodyReadTotal = 0
            while (bodyReadTotal < contentLength) {
                val read = delegate.read(bodyBytes, bodyReadTotal, contentLength - bodyReadTotal)
                if (read <= 0) break
                bodyReadTotal += read
            }

            val bodyText = String(bodyBytes, 0, bodyReadTotal, Charsets.UTF_8)
            val fullResponse = headerText + bodyText

            hasSanitizedSdp = true
            val sanitized = sanitizeSdpResponse(fullResponse)
            bufferedData = ByteArrayInputStream(sanitized.toByteArray(Charsets.UTF_8))
        } else {
            bufferedData = ByteArrayInputStream(headerBaos.toByteArray())
        }
    }

    private fun sanitizeSdpResponse(response: String): String {
        if (response.contains("a=fmtp:", ignoreCase = true)) {
            return response
        }

        val h265Matcher = Regex("""a=rtpmap:(\d+)\s+H265/90000""", RegexOption.IGNORE_CASE)
        val h264Matcher = Regex("""a=rtpmap:(\d+)\s+H264/90000""", RegexOption.IGNORE_CASE)

        var sanitizedBody = response

        val h265Match = h265Matcher.find(response)
        if (h265Match != null) {
            val pt = h265Match.groupValues[1]
            val fmtpLine = "a=fmtp:$pt sprop-vps=QAEMAf//AWAAAAMAAAMAAAMAAAMAlC2A;sprop-sps=QgEBAWAAAAMAAAMAAAMAAAMAlA8A8Cq1;sprop-pps=RAEAAhXw"
            sanitizedBody = response.replace(h265Match.value, "${h265Match.value}\r\n$fmtpLine")
        } else {
            val h264Match = h264Matcher.find(response)
            if (h264Match != null) {
                val pt = h264Match.groupValues[1]
                val fmtpLine = "a=fmtp:$pt sprop-parameter-sets=Z0LADJp0BEAIn5cACAwgAAB1AAdI,aM48gA=="
                sanitizedBody = response.replace(h264Match.value, "${h264Match.value}\r\n$fmtpLine")
            }
        }

        val headerBodySplit = sanitizedBody.split("\r\n\r\n", limit = 2)
        if (headerBodySplit.size == 2) {
            val headers = headerBodySplit[0]
            val body = headerBodySplit[1]
            val bodyLengthBytes = body.toByteArray(Charsets.UTF_8).size

            val updatedHeaders = headers.replace(Regex("""Content-[Ll]ength:\s*\d+""", RegexOption.IGNORE_CASE), "Content-Length: $bodyLengthBytes")
            val finalResult = "$updatedHeaders\r\n\r\n$body"
            Log.d("SdpSanitizer", "Sanitized RTSP DESCRIBE response:\n$finalResult")
            return finalResult
        }

        return sanitizedBody
    }
}
