package com.tencent.bkrepo.repository.interceptor

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.manager.PrincipalManager
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.proxy.HttpProxyUtil
import com.tencent.bkrepo.common.service.util.proxy.ProxyCallHandler
import com.tencent.bkrepo.repository.config.DriveProperties
import com.tencent.bkrepo.repository.message.RepositoryMessageCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.util.AntPathMatcher
import org.springframework.web.servlet.HandlerInterceptor

class DriveProxyInterceptor(
    private val proxyHandler: ProxyCallHandler,
    private val properties: DriveProperties,
    private val principalManagerProvider: ObjectProvider<PrincipalManager>
) : HandlerInterceptor {

    private val httpProxyUtil = HttpProxyUtil()
    private val pathMatcher = AntPathMatcher()

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val requestURI = request.requestURI
        val requestMethod = request.method

        // 验证请求是否在白名单中
        if (!isRequestAllowed(requestMethod, requestURI)) {
            logger.warn("[DriveProxy] Request [$requestMethod $requestURI] is not in the allowed API whitelist")
            throw ErrorCodeException(
                RepositoryMessageCode.DRIVE_API_NOT_ALLOWED,
                "API [$requestMethod $requestURI] is not allowed"
            )
        }

        // 权限检查：要求用户已认证（GENERAL 类型）
        checkPermission()

        // 验证配置
        validateConfiguration()
        httpProxyUtil.proxy(
            proxyRequest = request,
            proxyResponse = response,
            targetUrl = properties.ciServer,
            prefix = DRIVE_API_PREFIX,
            proxyCallHandler = proxyHandler
        )

        return false
    }

    /**
     * 权限检查
     */
    private fun checkPermission() {
        val userId = SecurityUtils.getUserId()

        try {
            // 从 ObjectProvider 获取 PrincipalManager 实例
            val principalManager = principalManagerProvider.getObject()
            principalManager.checkPrincipal(userId, PrincipalType.GENERAL)
            logger.debug("[DriveProxy] User[$userId] permission check passed for drive request")
        } catch (exception: PermissionException) {
            logger.warn("[DriveProxy] User[$userId] permission check failed for drive request: ${exception.message}")
            throw exception
        }
    }

    /**
     * 验证配置是否完整
     */
    private fun validateConfiguration() {
        require(
            properties.ciServer.isNotBlank() &&
                properties.bkAppCode.isNotBlank() &&
                properties.bkAppSecret.isNotBlank()
        ) {
            throw ErrorCodeException(
                RepositoryMessageCode.BKDIRVE_CONFIG_ERROR,
                "CI server URL or token is not configured"
            )
        }
    }

    /**
     * 检查请求是否在白名单中
     * @param method HTTP方法
     * @param uri 请求URI
     * @return 是否允许访问
     */
    private fun isRequestAllowed(method: String, uri: String): Boolean {
        // 如果白名单为空，拒绝所有请求（安全优先）
        if (properties.allowedApis.isEmpty()) {
            logger.warn("[DriveProxy] Allowed API whitelist is empty, rejecting all requests")
            return false
        }

        // 获取该HTTP方法对应的路径模式列表
        val pathPatterns = properties.allowedApis[method.uppercase()] ?: return false

        // 检查请求路径是否匹配任一模式
        return pathPatterns.any { pattern -> pathMatcher.match(pattern, uri) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveProxyInterceptor::class.java)
        private const val DRIVE_API_PREFIX = "/api/drive/ci"
    }
}