package com.sprillex.rtspconfig

import java.net.URI

object RtspUriUtils {

    fun isValidRtspUri(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val trimmed = uriString.trim()
        val lower = trimmed.lowercase()
        if (!lower.startsWith("rtsp://") && !lower.startsWith("rtsps://")) {
            return false
        }
        return try {
            val uri = URI(trimmed)
            !uri.host.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    fun maskCredentials(uriString: String?): String {
        if (uriString.isNullOrBlank()) return "[EMPTY]"
        val trimmed = uriString.trim()
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd == -1) return trimmed

        val afterScheme = trimmed.substring(schemeEnd + 3)
        val atIndex = afterScheme.indexOf('@')
        if (atIndex == -1) return trimmed

        val userInfo = afterScheme.substring(0, atIndex)
        val rest = afterScheme.substring(atIndex)

        val maskedUserInfo = if (userInfo.contains(":")) {
            val parts = userInfo.split(":", limit = 2)
            "${parts[0]}:****"
        } else {
            "****"
        }

        val scheme = trimmed.substring(0, schemeEnd + 3)
        return "$scheme$maskedUserInfo$rest"
    }

    fun buildRtspUri(
        host: String,
        port: Int,
        path: String,
        user: String = "",
        pass: String = ""
    ): String {
        val cleanHost = host.trim()
        val cleanPort = if (port in 1..65535) port else 554
        val auth = when {
            user.isNotBlank() && pass.isNotBlank() -> "${user.trim()}:${pass.trim()}@"
            user.isNotBlank() -> "${user.trim()}@"
            else -> ""
        }
        val cleanPath = when {
            path.isBlank() -> ""
            path.startsWith("/") -> path.trim()
            else -> "/${path.trim()}"
        }
        return "rtsp://$auth$cleanHost:$cleanPort$cleanPath"
    }
}
