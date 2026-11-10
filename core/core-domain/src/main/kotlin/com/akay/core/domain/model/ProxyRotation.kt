package com.akay.core.domain.model

/**
 * Pure selection rules for the proxy list. WebView application lives in ProxyManager;
 * this object is what the index, the saved proxy id, and failover all have to agree on.
 */
object ProxyRotation {

    fun isEligible(proxy: ProxyServer): Boolean {
        if (!proxy.enabled || proxy.port !in 1..65535) return false
        val host = ProxyAddresses.extract(proxy.host).first
        if (host.isBlank()) return false
        if (host.any { it.isWhitespace() || it == '/' || it == '@' || it == '[' || it == ']' }) return false
        return true
    }

    fun eligible(proxies: List<ProxyServer>): List<ProxyServer> = proxies.filter(::isEligible)

    fun indexOf(eligible: List<ProxyServer>, index: Int, activeId: String?): Int {
        if (eligible.isEmpty()) return 0
        if (!activeId.isNullOrBlank()) {
            val byId = eligible.indexOfFirst { it.id == activeId }
            if (byId >= 0) return byId
        }
        return index.coerceIn(0, eligible.lastIndex)
    }

    fun select(eligible: List<ProxyServer>, index: Int, activeId: String?): ProxyServer? {
        if (eligible.isEmpty()) return null
        return eligible[indexOf(eligible, index, activeId)]
    }

    /** Next index in [0, size). An empty list stays at 0; a single proxy stays put. */
    fun nextIndex(current: Int, size: Int): Int {
        if (size <= 0) return 0
        val base = if (current in 0 until size) current else current.coerceIn(0, size - 1)
        return (base + 1) % size
    }

    /** [startIndex] first, then the rest, wrapping. Used when the chosen rule is rejected. */
    fun failoverOrder(eligible: List<ProxyServer>, startIndex: Int): List<ProxyServer> {
        if (eligible.isEmpty()) return emptyList()
        val start = startIndex.coerceIn(0, eligible.lastIndex)
        return List(eligible.size) { offset -> eligible[(start + offset) % eligible.size] }
    }
}
