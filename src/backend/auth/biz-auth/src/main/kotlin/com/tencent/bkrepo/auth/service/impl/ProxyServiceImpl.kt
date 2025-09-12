/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.auth.service.impl

import com.tencent.bkrepo.auth.dao.ProxyDao
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TProxy
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.proxy.ProxyCreateRequest
import com.tencent.bkrepo.auth.pojo.proxy.ProxyInfo
import com.tencent.bkrepo.auth.pojo.proxy.ProxyKey
import com.tencent.bkrepo.auth.pojo.proxy.ProxyListOption
import com.tencent.bkrepo.auth.pojo.proxy.ProxyStatus
import com.tencent.bkrepo.auth.pojo.proxy.ProxyStatusRequest
import com.tencent.bkrepo.auth.pojo.proxy.ProxyUpdateRequest
import com.tencent.bkrepo.auth.service.ProxyService
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.api.util.UrlFormatter
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.AESUtils
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.metadata.pojo.router.RouterNodeType
import com.tencent.bkrepo.common.metadata.pojo.router.AddRouterNodeRequest
import com.tencent.bkrepo.common.metadata.pojo.router.RemoveRouterNodeRequest
import com.tencent.bkrepo.common.metadata.service.router.RouterAdminService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import kotlin.random.Random

@Service
class ProxyServiceImpl(
    private val proxyDao: ProxyDao,
    private val permissionManager: PermissionManager,
    private val routerAdminService: RouterAdminService
) : ProxyService {
    override fun create(request: ProxyCreateRequest): ProxyInfo {
        permissionManager.checkProjectPermission(PermissionAction.MANAGE, request.projectId)
        val userId = SecurityUtils.getUserId()
        var name = randomString(PROXY_NAME_LEN)
        while (checkExist(request.projectId, name)) {
            name = randomString(PROXY_NAME_LEN)
        }
        val secretKey = AESUtils.encrypt(randomString(PROXY_KEY_LEN))
        val tProxy = TProxy(
            name = name,
            displayName = request.displayName,
            projectId = request.projectId,
            clusterName = request.clusterName,
            domain = UrlFormatter.formatHost(request.domain),
            ip = StringPool.UNKNOWN,
            secretKey = secretKey,
            sessionKey = StringPool.EMPTY,
            ticket = secureRandom.nextInt(),
            ticketCreateInstant = Instant.now(),
            syncRateLimit = request.syncRateLimit.toBytes(),
            syncTimeRange = request.syncTimeRange,
            cacheExpireDays = request.cacheExpireDays,
            createdBy = userId,
            createdDate = LocalDateTime.now(),
            lastModifiedBy = userId,
            lastModifiedDate = LocalDateTime.now(),
            status = ProxyStatus.CREATE
        )
        return proxyDao.insert(tProxy).convert()
    }

    override fun getInfo(projectId: String, name: String): ProxyInfo {
        val tProxy = proxyDao.findByProjectIdAndName(projectId, name)
            ?: throw ErrorCodeException(AuthMessageCode.AUTH_PROXY_NOT_EXIST, name)
        permissionManager.checkProjectPermission(PermissionAction.READ, tProxy.projectId)
        return tProxy.convert()
    }

    override fun page(projectId: String, option: ProxyListOption): Page<ProxyInfo> {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId)
        val pageRequest = Pages.ofRequest(option.pageNumber, option.pageSize)
        val page = proxyDao.findByOption(projectId, option)
        return Pages.ofResponse(pageRequest, page.totalElements, page.content.map { it.convert() })
    }

    override fun getEncryptedKey(projectId: String, name: String): ProxyKey {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId)
        val tProxy = proxyDao.findByProjectIdAndName(projectId, name)
            ?: throw ErrorCodeException(AuthMessageCode.AUTH_PROXY_NOT_EXIST, name)
        return ProxyKey(tProxy.secretKey, tProxy.sessionKey)
    }

    override fun update(request: ProxyUpdateRequest): ProxyInfo {
        val userId = SecurityUtils.getUserId()
        val tProxy = proxyDao.findByProjectIdAndName(request.projectId, request.name)
            ?: throw ErrorCodeException(AuthMessageCode.AUTH_PROXY_NOT_EXIST, request.name)
        permissionManager.checkProjectPermission(PermissionAction.MANAGE, tProxy.projectId)
        request.displayName?.let { tProxy.displayName = it }
        request.domain?.let {
            tProxy.domain = UrlFormatter.formatHost(it)
            addRouterNode(tProxy)
        }
        request.syncRateLimit?.let { tProxy.syncRateLimit = it.toBytes() }
        request.syncTimeRange?.let { tProxy.syncTimeRange = it }
        request.cacheExpiredDays?.let { tProxy.cacheExpireDays = it }
        tProxy.lastModifiedBy = userId
        tProxy.lastModifiedDate = LocalDateTime.now()
        logger.info("user[$userId] update proxy with request[$request]")
        return proxyDao.save(tProxy).convert()
    }

    override fun delete(projectId: String, name: String) {
        permissionManager.checkProjectPermission(PermissionAction.MANAGE, projectId)
        proxyDao.findByProjectIdAndName(projectId, name)
            ?: throw ErrorCodeException(AuthMessageCode.AUTH_PROXY_NOT_EXIST, name)
        proxyDao.deleteByProjectIdAndName(projectId, name)
        routerAdminService.removeRouterNode(RemoveRouterNodeRequest(name, SecurityUtils.getUserId()))
    }

    override fun ticket(projectId: String, name: String): Int {
        val tProxy = proxyDao.findByProjectIdAndName(projectId, name)
            ?: throw ErrorCodeException(AuthMessageCode.AUTH_PROXY_NOT_EXIST, name)

        return if (!tProxy.ticketCreateInstant.plusSeconds(15).isAfter(Instant.now())) {
            val ticket = Random.nextInt()
            tProxy.ticket = ticket
            tProxy.ticketCreateInstant = Instant.now()
            proxyDao.save(tProxy)
            ticket
        } else {
            tProxy.ticket
        }
    }

    override fun startup(request: ProxyStatusRequest): String {
        with(request) {
            val tProxy = proxyDao.findByProjectIdAndName(projectId, name)
                ?: throw ErrorCodeException(AuthMessageCode.AUTH_PROXY_NOT_EXIST, name)
            val secretKey = AESUtils.decrypt(tProxy.secretKey)
            Preconditions.checkArgument(
                expression = tProxy.ticketCreateInstant.plusSeconds(N_EXPIRED_SEC).isAfter(Instant.now()),
                name = TProxy::ticket.name
            )
            Preconditions.checkArgument(
                expression = AESUtils.encrypt("$name:$STARTUP_OPERATION:${tProxy.ticket}", secretKey) == message,
                name = message
            )
            val sessionKey = AESUtils.encrypt(randomString(PROXY_KEY_LEN), secretKey)
            tProxy.status = ProxyStatus.ONLINE
            tProxy.sessionKey = sessionKey
            tProxy.ip = HttpContextHolder.getClientAddress()
            proxyDao.save(tProxy)
            addRouterNode(tProxy)
            return sessionKey
        }
    }

    private fun addRouterNode(tProxy: TProxy) {
        routerAdminService.addRouterNode(
            AddRouterNodeRequest(
                id = tProxy.name,
                name = tProxy.displayName,
                description = StringPool.EMPTY,
                type = RouterNodeType.PROXY,
                location = tProxy.domain,
                operator = tProxy.createdBy
            )
        )
    }

    override fun shutdown(request: ProxyStatusRequest) {
        with(request) {
            val tProxy = proxyDao.findByProjectIdAndName(projectId, name)
                ?: throw ErrorCodeException(AuthMessageCode.AUTH_PROXY_NOT_EXIST, name)
            val secretKey = AESUtils.decrypt(tProxy.secretKey)
            Preconditions.checkArgument(
                expression = tProxy.ticketCreateInstant.plusSeconds(N_EXPIRED_SEC).isAfter(Instant.now()),
                name = TProxy::ticket.name
            )
            Preconditions.checkArgument(
                expression = AESUtils.encrypt("$name:$SHUTDOWN_OPERATION:${tProxy.ticket}", secretKey) == message,
                name = ProxyStatusRequest::message.name
            )
            tProxy.status = ProxyStatus.OFFLINE
            tProxy.sessionKey = StringPool.EMPTY
            proxyDao.save(tProxy)
        }
    }

    override fun heartbeat(projectId: String, name: String) {
        val tProxy = proxyDao.findByProjectIdAndName(projectId, name)
            ?: throw ErrorCodeException(AuthMessageCode.AUTH_PROXY_NOT_EXIST, name)
        tProxy.heartbeatTime = LocalDateTime.now()
        proxyDao.save(tProxy)
    }

    private fun checkExist(projectId: String, name: String): Boolean {
        return proxyDao.findByProjectIdAndName(projectId, name) != null
    }

    private fun TProxy.convert() = ProxyInfo(
        name = name,
        displayName = displayName,
        projectId = projectId,
        clusterName = clusterName,
        domain = domain,
        ip = ip,
        status = status,
        syncRateLimit = syncRateLimit,
        syncTimeRange = syncTimeRange,
        cacheExpireDays = cacheExpireDays
    )

    private fun randomString(length: Int): String {
        val buffer = ByteArray(length / 2)
        SecureRandom().nextBytes(buffer)
        return buffer.joinToString("") { String.format("%02x", it) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProxyServiceImpl::class.java)
        private const val N_EXPIRED_SEC = 30L
        private const val STARTUP_OPERATION = "startup"
        private const val SHUTDOWN_OPERATION = "shutdown"

        private const val PROXY_NAME_LEN = 10
        private const val PROXY_KEY_LEN = 32

        private val secureRandom = SecureRandom()
    }
}
