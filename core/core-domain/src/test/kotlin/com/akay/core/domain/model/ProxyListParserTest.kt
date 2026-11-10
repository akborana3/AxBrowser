package com.akay.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProxyListParserTest {

    @Test
    fun emptyAndCommentsProduceNothing() {
        assertTrue(ProxyListParser.parse(null).proxies.isEmpty())
        assertTrue(ProxyListParser.parse(null).errors.isEmpty())
        val blank = ProxyListParser.parse("  \n\n# comment\n// also\n  ")
        assertTrue(blank.proxies.isEmpty())
        assertTrue(blank.errors.isEmpty())
    }

    @Test
    fun parsesHostPortAndSchemes() {
        val http = entry("1.2.3.4:8080")
        assertEquals(ProxyType.HTTP, http.type)
        assertEquals("1.2.3.4", http.host)
        assertEquals(8080, http.port)
        assertNull(http.username)

        val https = entry("HTTPS://proxy.example:443")
        assertEquals(ProxyType.HTTPS, https.type)
        assertEquals("proxy.example", https.host)
        assertEquals(443, https.port)

        val socks = entry("socks4a://10.0.0.1:1080")
        assertEquals(ProxyType.SOCKS4, socks.type)
    }

    @Test
    fun credentialsWithReservedCharacters() {
        val atForm = entry("socks5://user:p%40ss:word@[2001:db8::1]:1080")
        assertEquals(ProxyType.SOCKS5, atForm.type)
        assertEquals("user", atForm.username)
        assertEquals("p@ss:word", atForm.password)
        assertEquals("2001:db8::1", atForm.host)
        assertEquals(1080, atForm.port)
        assertTrue(!atForm.remoteDns)

        val colonForm = entry("10.0.0.8:9050:alice:pa:ss")
        assertEquals("alice", colonForm.username)
        assertEquals("pa:ss", colonForm.password)

        val lastAt = entry("http://user:p@ss@203.0.113.5:8080")
        assertEquals("user", lastAt.username)
        assertEquals("p@ss", lastAt.password)
        assertEquals("203.0.113.5", lastAt.host)
    }

    @Test
    fun socks5hSetsRemoteDns() {
        val parsed = entry("socks5h://[2001:db8::2]:9050")
        assertEquals(ProxyType.SOCKS5, parsed.type)
        assertTrue(parsed.remoteDns)
        assertEquals("2001:db8::2", parsed.host)
    }

    @Test
    fun rejectsMalformedLinesWithoutDroppingTheRest() {
        val batch = ProxyListParser.parse(
            """
            1.2.3.4:8080
            not a proxy
            :8080
            example.com:99999
            2001:db8::1:1080
            socks5://[2001:db8::5]:1080

            """.trimIndent()
        )
        assertEquals(listOf("1.2.3.4", "2001:db8::5"), batch.proxies.map { it.host })
        assertEquals(4, batch.errors.size)
        assertTrue(batch.errors.any { it.message.contains("brackets") })
        assertTrue(batch.errors.any { it.message.contains("spaces") })
        assertTrue(batch.errors.any { it.line.contains("99999") })
    }

    @Test
    fun rulesAndDownloadUrls() {
        val server = ProxyServer(
            id = "1",
            label = "v6",
            type = ProxyType.SOCKS5,
            host = "2001:db8::1",
            port = 1080,
            username = "user",
            password = "a@b:c d",
            enabled = true,
            sortOrder = 0,
            remoteDns = true
        )
        assertEquals("socks5://[2001:db8::1]:1080", server.toProxyRule())
        assertEquals("socks5h://user:a%40b%3Ac%20d@[2001:db8::1]:1080", server.toDownloadProxyUrl())
        assertTrue(!server.toProxyRule().contains("@"))

        val bracketed = server.copy(host = "[2001:db8::1]")
        assertEquals("socks5://[2001:db8::1]:1080", bracketed.toProxyRule())

        val embedded = ProxyServer(
            id = "2", label = "v4", type = ProxyType.HTTP, host = "203.0.113.9:8080",
            port = 8080, username = null, password = null, enabled = true, sortOrder = 0
        )
        assertEquals("http://203.0.113.9:8080", embedded.toProxyRule())
    }

    @Test
    fun authChallengeDoesNotMatchABlankHost() {
        assertTrue(!ProxyAddresses.matchesChallenge("1.2.3.4", null))
        assertTrue(!ProxyAddresses.matchesChallenge("1.2.3.4", ""))
        assertTrue(ProxyAddresses.matchesChallenge("1.2.3.4", "1.2.3.4"))
        assertTrue(ProxyAddresses.matchesChallenge("1.2.3.4", "1.2.3.4:8080"))
        assertTrue(ProxyAddresses.matchesChallenge("2001:db8::1", "[2001:db8::1]:1080"))
        assertTrue(!ProxyAddresses.matchesChallenge("1.2.3.4", "example.com"))
    }

    private fun entry(line: String): ParsedProxy {
        val outcome = ProxyListParser.parseEntry(line)
        assertTrue(outcome is ProxyParseOutcome.Ok, (outcome as? ProxyParseOutcome.Invalid)?.message)
        return (outcome as ProxyParseOutcome.Ok).proxy
    }
}
