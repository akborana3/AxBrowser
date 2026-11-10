package com.akay.core.data.proxy

import com.akay.core.domain.model.ProxyAddresses
import com.akay.core.domain.model.ProxyServer
import com.akay.core.domain.model.ProxyType
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Points an OkHttp client at whatever [current] returns at call time, so a
 * rotation is picked up by the next download without rebuilding the client.
 * HTTP proxy passwords are sent on 407. SOCKS passwords go through the
 * process-wide [java.net.Authenticator], which is what the platform SOCKS
 * socket asks; it only answers when the request is actually for the active
 * SOCKS proxy.
 */
object OkHttpProxies {

    fun apply(builder: OkHttpClient.Builder, current: () -> ProxyServer?): OkHttpClient.Builder {
        return builder
            .proxySelector(selector(current))
            .proxyAuthenticator(httpProxyAuthenticator(current))
    }

    fun installSocksAuthenticator(current: () -> ProxyServer?) {
        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                val proxy = current() ?: return null
                val user = proxy.username
                if (!proxy.usesSocks || user.isNullOrBlank()) return null
                val protocol = requestingProtocol?.lowercase().orEmpty()
                if (!protocol.contains("socks")) return null
                if (!ProxyAddresses.matchesChallenge(proxy.host, requestingHost)) return null
                return PasswordAuthentication(user, proxy.password.orEmpty().toCharArray())
            }
        })
    }

    private fun selector(current: () -> ProxyServer?) = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val proxy = current() ?: return listOf(Proxy.NO_PROXY)
            val type = when (proxy.type) {
                ProxyType.SOCKS4, ProxyType.SOCKS5 -> Proxy.Type.SOCKS
                ProxyType.HTTP, ProxyType.HTTPS -> Proxy.Type.HTTP
            }
            val host = ProxyAddresses.extract(proxy.host).first
            if (host.isBlank() || proxy.port !in 1..65535) return listOf(Proxy.NO_PROXY)
            return listOf(Proxy(type, InetSocketAddress.createUnresolved(host, proxy.port)))
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }

    private fun httpProxyAuthenticator(current: () -> ProxyServer?) = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.code != 407) return null
            if (response.request.header("Proxy-Authorization") != null) return null
            val proxy = current() ?: return null
            val user = proxy.username
            if (proxy.usesSocks || user.isNullOrBlank()) return null
            val credential = Credentials.basic(user, proxy.password.orEmpty(), Charsets.UTF_8)
            return response.request.newBuilder()
                .header("Proxy-Authorization", credential)
                .build()
        }
    }
}
