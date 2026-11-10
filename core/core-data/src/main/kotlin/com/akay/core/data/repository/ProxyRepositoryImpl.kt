package com.akay.core.data.repository

import com.akay.core.data.db.dao.ProxyDao
import com.akay.core.data.db.entity.ProxyEntity
import com.akay.core.data.security.CryptoManager
import com.akay.core.domain.model.ProxyAddresses
import com.akay.core.domain.model.ProxyServer
import com.akay.core.domain.model.ProxyType
import com.akay.core.domain.repository.ProxyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxyRepositoryImpl @Inject constructor(
    private val proxyDao: ProxyDao,
    private val cryptoManager: CryptoManager
) : ProxyRepository {

    override fun observeAll(): Flow<List<ProxyServer>> =
        proxyDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllOnce(): List<ProxyServer> =
        proxyDao.getAllOnce().map { it.toDomain() }

    override suspend fun save(
        id: String?,
        label: String,
        type: ProxyType,
        host: String,
        port: Int,
        username: String?,
        password: String?,
        enabled: Boolean,
        timezone: String?,
        locale: String?,
        remoteDns: Boolean
    ) {
        val sortOrder = if (id == null) {
            proxyDao.getMaxSortOrder() + 1
        } else {
            proxyDao.getAllOnce().firstOrNull { it.id == id }?.sortOrder ?: (proxyDao.getMaxSortOrder() + 1)
        }
        val (cleanHost, embeddedPort) = ProxyAddresses.extract(host)
        val storedPort = if (port in 1..65535) port else embeddedPort ?: port
        val entity = ProxyEntity(
            id = id ?: UUID.randomUUID().toString(),
            label = label,
            type = type.name,
            host = cleanHost.ifBlank { host.trim() },
            port = storedPort,
            username = username?.takeIf { it.isNotBlank() },
            encryptedPassword = password?.takeIf { it.isNotBlank() }?.let { cryptoManager.encrypt(it) },
            enabled = enabled,
            sortOrder = sortOrder,
            timezone = timezone?.trim()?.takeIf { it.isNotEmpty() },
            locale = locale?.trim()?.takeIf { it.isNotEmpty() },
            remoteDns = remoteDns
        )
        proxyDao.upsert(entity)
    }

    override suspend fun delete(id: String) {
        proxyDao.delete(id)
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        proxyDao.setEnabled(id, enabled)
    }

    private fun ProxyEntity.toDomain(): ProxyServer {
        val (cleanHost, embeddedPort) = ProxyAddresses.extract(host)
        return ProxyServer(
            id = id,
            label = label,
            type = runCatching { ProxyType.valueOf(type) }.getOrDefault(ProxyType.HTTP),
            host = cleanHost.ifBlank { host },
            port = if (port in 1..65535) port else embeddedPort ?: port,
            username = username,
            password = encryptedPassword?.let { runCatching { cryptoManager.decrypt(it) }.getOrNull() },
            enabled = enabled,
            sortOrder = sortOrder,
            timezone = timezone,
            locale = locale,
            remoteDns = remoteDns
        )
    }
}
