package com.akay.core.domain.model

data class ParsedProxy(
    val type: ProxyType,
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
    val remoteDns: Boolean,
    val label: String
)

data class ProxyLineError(val lineNumber: Int, val line: String, val message: String)

data class ProxyListParseResult(
    val proxies: List<ParsedProxy>,
    val errors: List<ProxyLineError>
)

sealed class ProxyParseOutcome {
    data class Ok(val proxy: ParsedProxy) : ProxyParseOutcome()
    data class Invalid(val message: String) : ProxyParseOutcome()
}

/**
 * Accepts one proxy per line. Blank lines and `#` / `//` comments are skipped.
 * Supported forms:
 * - `host:port`
 * - `host:port:user:password` (password may contain extra colons)
 * - `user:password@host:port` (password may contain `:` and `@`; the last `@` splits userinfo)
 * - `scheme://...` with scheme `http`, `https`, `socks4`, `socks4a`, `socks5`, `socks5h`
 * - the same forms with a bracketed IPv6 host, e.g. `socks5://user:p%40ss@[2001:db8::1]:1080`
 *
 * Unbracketed IPv6 is rejected: `2001:db8::1:1080` cannot be told apart from a hostname
 * that contains colons plus a port.
 */
object ProxyListParser {

    private val schemePrefix = Regex("""^(?i)(https?|socks4a|socks4|socks5h|socks5)://""")

    fun parse(text: String?): ProxyListParseResult {
        if (text.isNullOrBlank()) return ProxyListParseResult(emptyList(), emptyList())
        val proxies = mutableListOf<ParsedProxy>()
        val errors = mutableListOf<ProxyLineError>()
        text.split(Regex("""\r\n|\n|\r""")).forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) return@forEachIndexed
            when (val outcome = parseEntry(line)) {
                is ProxyParseOutcome.Ok -> proxies += outcome.proxy
                is ProxyParseOutcome.Invalid -> errors += ProxyLineError(index + 1, line.take(160), outcome.message)
            }
        }
        return ProxyListParseResult(proxies, errors)
    }

    fun parseEntry(text: String?): ProxyParseOutcome {
        val line = text?.trim().orEmpty()
        if (line.isEmpty()) return invalid("Proxy entry is empty.")
        if (line.startsWith("#") || line.startsWith("//")) return invalid("Comment is not a proxy.")
        if (line.any { it.isWhitespace() }) return invalid("Proxy entry can't contain spaces. Percent-encode them in the password.")

        var rest = line
        var type = ProxyType.HTTP
        var remoteDns = false
        val scheme = schemePrefix.find(rest)
        if (scheme != null) {
            rest = rest.substring(scheme.range.last + 1)
            when (scheme.groupValues[1].lowercase()) {
                "https" -> type = ProxyType.HTTPS
                "socks4", "socks4a" -> type = ProxyType.SOCKS4
                "socks5" -> type = ProxyType.SOCKS5
                "socks5h" -> {
                    type = ProxyType.SOCKS5
                    remoteDns = true
                }
                else -> type = ProxyType.HTTP
            }
        }
        if (rest.isBlank()) return invalid("Missing host and port.")

        var username: String? = null
        var password: String? = null
        var hostPort = rest
        val at = rest.lastIndexOf('@')
        if (at >= 0) {
            val userInfo = rest.substring(0, at)
            hostPort = rest.substring(at + 1)
            if (userInfo.isEmpty()) return invalid("Missing username before @.")
            val decoded = decodeUserInfo(userInfo)
            if (decoded == null) return invalid("Credentials contain a bad percent-escape.")
            username = decoded.first
            password = decoded.second
            if (username.isNullOrBlank()) return invalid("Missing username.")
        }

        val endpoint = splitHostPort(hostPort) ?: return invalid(hostPortMessage(hostPort))
        if (username == null && endpoint.username != null) {
            username = endpoint.username
            password = endpoint.password
        } else if (username != null && endpoint.username != null) {
            return invalid("Credentials were specified twice.")
        }
        if (endpoint.port !in 1..65535) return invalid("Port ${endpoint.port} is outside 1-65535.")
        if (!endpoint.host.isValidProxyHost()) return invalid("Host is invalid.")

        val label = "${type.name.lowercase()}://${endpoint.host}:${endpoint.port}"
        return ProxyParseOutcome.Ok(
            ParsedProxy(
                type = type,
                host = endpoint.host,
                port = endpoint.port,
                username = username,
                password = password,
                remoteDns = remoteDns,
                label = label
            )
        )
    }

    private data class Endpoint(val host: String, val port: Int, val username: String?, val password: String?)

    private fun splitHostPort(hostPort: String): Endpoint? {
        if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            if (end <= 1) return null
            val host = hostPort.substring(1, end).substringBefore('%').trim()
            val rest = hostPort.substring(end + 1)
            if (!rest.startsWith(":")) return null
            return endpointFromPieces(host, rest.substring(1).split(':'))
        }
        if (hostPort.count { it == ':' } >= 2 && !hostPort.contains('.')) return null
        val pieces = hostPort.split(':')
        if (pieces.size < 2) return null
        return endpointFromPieces(pieces.first(), pieces.drop(1))
    }

    private fun endpointFromPieces(host: String, afterHost: List<String>): Endpoint? {
        if (afterHost.isEmpty()) return null
        val port = afterHost[0].toIntOrNull() ?: return null
        return when (afterHost.size) {
            1 -> Endpoint(host, port, null, null)
            2 -> afterHost[1].takeIf { it.isNotEmpty() }?.let { Endpoint(host, port, it, null) }
            else -> {
                val user = afterHost[1]
                if (user.isEmpty()) null else Endpoint(host, port, user, afterHost.drop(2).joinToString(":"))
            }
        }
    }

    private fun decodeUserInfo(userInfo: String): Pair<String, String?>? {
        val colon = userInfo.indexOf(':')
        val userRaw = if (colon < 0) userInfo else userInfo.substring(0, colon)
        val passRaw = if (colon < 0) null else userInfo.substring(colon + 1)
        val user = percentDecode(userRaw) ?: return null
        val pass = if (passRaw == null) null else percentDecode(passRaw) ?: return null
        return user to pass
    }

    private fun percentDecode(raw: String): String? {
        if (!raw.contains('%')) return raw
        val bytes = ArrayList<Byte>(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '%') {
                if (i + 2 >= raw.length) return null
                val value = raw.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                bytes += value.toByte()
                i += 3
            } else {
                c.toString().toByteArray(Charsets.UTF_8).forEach { bytes += it }
                i++
            }
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private fun hostPortMessage(hostPort: String): String {
        val unbracketedIpv6 = !hostPort.startsWith("[") && hostPort.count { it == ':' } >= 2 && !hostPort.contains('.')
        return if (unbracketedIpv6) {
            "Wrap IPv6 addresses in brackets, for example [2001:db8::1]:1080."
        } else {
            "Need a host and a port from 1 to 65535."
        }
    }

    private fun String.isValidProxyHost(): Boolean {
        if (isBlank()) return false
        if (any { it.isWhitespace() || it == '/' || it == '@' || it == '[' || it == ']' }) return false
        return true
    }

    private fun invalid(message: String) = ProxyParseOutcome.Invalid(message)
}
