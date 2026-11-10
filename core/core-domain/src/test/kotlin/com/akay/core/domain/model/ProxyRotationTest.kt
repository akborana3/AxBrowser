package com.akay.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProxyRotationTest {

    @Test
    fun eligibleSkipsDisabledBlankAndBadPorts() {
        val proxies = listOf(
            proxy("ok", enabled = true, host = "1.1.1.1", port = 8080),
            proxy("off", enabled = false, host = "1.1.1.1", port = 8080),
            proxy("blank", enabled = true, host = "  ", port = 8080),
            proxy("port", enabled = true, host = "1.1.1.1", port = 0),
            proxy("high", enabled = true, host = "1.1.1.1", port = 70_000),
            proxy("space", enabled = true, host = "bad host", port = 1)
        )
        assertEquals(listOf("ok"), ProxyRotation.eligible(proxies).map { it.id })
    }

    @Test
    fun selectPrefersALiveIdThenClampsTheIndex() {
        val proxies = listOf(
            proxy("a", host = "1.1.1.1"),
            proxy("b", host = "2.2.2.2"),
            proxy("c", host = "3.3.3.3")
        )
        assertEquals("b", ProxyRotation.select(proxies, index = 0, activeId = "b")?.id)
        assertEquals("c", ProxyRotation.select(proxies, index = 50, activeId = null)?.id)
        assertEquals("c", ProxyRotation.select(proxies, index = 50, activeId = "missing")?.id)
        assertEquals("a", ProxyRotation.select(proxies, index = -4, activeId = "")?.id)
        assertNull(ProxyRotation.select(emptyList(), 3, "a"))
    }

    @Test
    fun nextIndexWrapsAndStaysPutForASingleProxy() {
        assertEquals(0, ProxyRotation.nextIndex(current = 0, size = 0))
        assertEquals(0, ProxyRotation.nextIndex(current = 0, size = 1))
        assertEquals(1, ProxyRotation.nextIndex(current = 0, size = 3))
        assertEquals(0, ProxyRotation.nextIndex(current = 2, size = 3))
        assertEquals(0, ProxyRotation.nextIndex(current = 9, size = 3))
    }

    @Test
    fun failoverStartsAtTheChosenProxyThenWraps() {
        val proxies = listOf(
            proxy("a", host = "1.1.1.1"),
            proxy("b", host = "2.2.2.2"),
            proxy("c", host = "3.3.3.3"),
            proxy("d", host = "4.4.4.4")
        )
        assertEquals(listOf("c", "d", "a", "b"), ProxyRotation.failoverOrder(proxies, 2).map { it.id })
        assertTrue(ProxyRotation.failoverOrder(emptyList(), 0).isEmpty())
    }

    private fun proxy(
        id: String,
        enabled: Boolean = true,
        host: String,
        port: Int = 8080
    ) = ProxyServer(
        id = id,
        label = id,
        type = ProxyType.HTTP,
        host = host,
        port = port,
        username = null,
        password = null,
        enabled = enabled,
        sortOrder = 0
    )
}
