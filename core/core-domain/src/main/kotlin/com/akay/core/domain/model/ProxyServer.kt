package com.akay.core.domain.model

enum class ProxyType { HTTP, HTTPS, SOCKS4, SOCKS5 }

enum class ProxyRotationMode { MANUAL, PER_NAVIGATION, TIMED }

data class ProxyServer(
    val id: String,
    val label: String,
    val type: ProxyType,
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
    val enabled: Boolean,
    val sortOrder: Int,
    /** IANA zone id, applied to the page only while this proxy is the active exit. Null leaves the device zone. */
    val timezone: String? = null,
    /** BCP 47 tag (en-US). Null leaves the device locale. */
    val locale: String? = null,
    /** SOCKS5h: the proxy resolves destination hostnames. WebView cannot express this; downloads can. */
    val remoteDns: Boolean = false
) {
    /**
     * The rule string Android WebView's ProxyController expects, e.g.
     * "socks5://1.2.3.4:1080" or "http://[2001:db8::1]:8080".
     * Chromium's proxy grammar has no userinfo and rejects the whole rule
     * ("Invalid Proxy URL") if credentials are embedded. HTTP proxy auth is
     * answered from [WebViewClient.onReceivedHttpAuthRequest]; SOCKS auth is
     * not supported by WebView and is only attached to download clients.
     */
    fun toProxyRule(): String {
        val scheme = when (type) {
            ProxyType.HTTP -> "http"
            ProxyType.HTTPS -> "https"
            ProxyType.SOCKS4 -> "socks4"
            ProxyType.SOCKS5 -> "socks5"
        }
        return "$scheme://${ProxyAddresses.literal(host)}:$port"
    }

    /**
     * Proxy URL for clients that do understand userinfo (yt-dlp, and similar).
     * SOCKS5 with [remoteDns] uses `socks5h` so the destination hostname is not
     * resolved on the device.
     */
    fun toDownloadProxyUrl(): String {
        val scheme = when (type) {
            ProxyType.HTTP -> "http"
            ProxyType.HTTPS -> "https"
            ProxyType.SOCKS4 -> "socks4"
            ProxyType.SOCKS5 -> if (remoteDns) "socks5h" else "socks5"
        }
        val auth = if (!username.isNullOrEmpty()) {
            "${ProxyAddresses.encodeUserInfo(username)}:${ProxyAddresses.encodeUserInfo(password.orEmpty())}@"
        } else ""
        return "$scheme://$auth${ProxyAddresses.literal(host)}:$port"
    }

    val requiresAuth: Boolean get() = !username.isNullOrBlank()

    val usesSocks: Boolean get() = type == ProxyType.SOCKS4 || type == ProxyType.SOCKS5
}
