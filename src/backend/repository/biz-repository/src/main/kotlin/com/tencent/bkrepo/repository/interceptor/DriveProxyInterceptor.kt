package com.tencent.bkrepo.repository.interceptor

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.manager.PrincipalManager
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.proxy.HttpProxyUtil
import com.tencent.bkrepo.common.service.util.proxy.ProxyCallHandler
import com.tencent.bkrepo.repository.config.DriveProperties
import com.tencent.bkrepo.repository.message.RepositoryMessageCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.servlet.HandlerInterceptor
import java.util.concurrent.TimeUnit

class DriveProxyInterceptor(
    private val proxyHandler: ProxyCallHandler,
    private val properties: DriveProperties,
    private val principalManagerProvider: ObjectProvider<PrincipalManager>
) : HandlerInterceptor {
    
    private val client = HttpClientBuilderFactory.create()
        .readTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(15, TimeUnit.MINUTES)
        .build()
    private val httpProxyUtil = HttpProxyUtil(client)

    override fun preHandle(
        request: HttpServletRequest, 
        response: HttpServletResponse, 
        handler: Any
    ): Boolean {
        val requestURI = request.requestURI
        
        // 只处理 /api/drive/** 路径的请求
        if (!requestURI.startsWith(DRIVE_API_PREFIX)) {
            return true
        }
        
        // 权限检查：要求用户已认证（GENERAL 类型）
        checkPermission(request)
        
        // 验证配置
        validateConfiguration()
        val targetGateway = request.getHeader(HEADER_DEVOPS_TARGET) ?: DEFAULT_TARGET_GATEWAY

        logger.info("Intercepting drive request: [${request.method}] $requestURI")
        try {
            // 使用 HttpProxyUtil 进行代理转发
            httpProxyUtil.proxy(
                proxyRequest = request,
                proxyResponse = response,
                targetUrl = properties.ciServer + "/ms/${targetGateway}/api/open",
                prefix = DRIVE_API_PREFIX,
                proxyCallHandler = proxyHandler
            )
            
            logger.info("Drive request forwarded successfully: [${request.method}] $requestURI")
        } catch (e: Exception) {
            logger.error("Failed to forward drive request: [${request.method}] $requestURI", e)
            throw ErrorCodeException(
                RepositoryMessageCode.BKDIRVE_CONFIG_ERROR,
                "Failed to forward request: ${e.message}"
            )
        }

        return false
    }
    
    /**
     * 权限检查
     */
    private fun checkPermission(request: HttpServletRequest) {
        val userId = request.getAttribute(USER_KEY) as? String ?: ANONYMOUS_USER
        
        try {
            // 从 ObjectProvider 获取 PrincipalManager 实例
            val principalManager = principalManagerProvider.getObject()
            principalManager.checkPrincipal(userId, PrincipalType.GENERAL)
            logger.debug("User[$userId] permission check passed for drive request")
        } catch (exception: PermissionException) {
            logger.warn("User[$userId] permission check failed for drive request: ${exception.message}")
            throw exception
        }
    }
    
    /**
     * 验证配置是否完整
     */
    private fun validateConfiguration() {
        require(properties.ciServer.isNotBlank() && properties.ciToken.isNotBlank()) {
            throw ErrorCodeException(
                RepositoryMessageCode.BKDIRVE_CONFIG_ERROR,
                "CI server URL or token is not configured"
            )
        }
    }
    
    companion object {
        private val logger = LoggerFactory.getLogger(DriveProxyInterceptor::class.java)
        private const val DRIVE_API_PREFIX = "/api/drive"
        private const val HEADER_DEVOPS_TARGET = "X-DEVOPS-PROXY-TARGET"
        private const val DEFAULT_TARGET_GATEWAY = "artifactory"
    }
}