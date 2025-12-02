package com.tencent.bkrepo.repository.interceptor

import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.proxy.DefaultProxyCallHandler
import com.tencent.bkrepo.repository.config.DriveProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import okhttp3.Request

/**
 * BkDrive 代理请求处理器
 * 负责在请求转发前后添加必要的请求头和处理响应
 */
class DriveProxyHandler(
    private val properties: DriveProperties
) : DefaultProxyCallHandler() {

    /**
     * 请求转发前的处理
     * 使用白名单机制：默认丢弃所有原始请求头，只保留白名单中指定的请求头，然后添加业务认证头
     */
    override fun before(
        proxyRequest: HttpServletRequest,
        proxyResponse: HttpServletResponse,
        request: Request
    ): Request {
        val userId = SecurityUtils.getUserId()
        
        // 构建新请求
        val builder = Request.Builder()
            .url(request.url)
            .method(request.method, request.body)
        
        // 添加白名单中的请求头
        addAllowedHeaders(builder, request.headers)
        
        // 添加业务认证头
        addAuthHeaders(builder, userId)
        
        return builder.build()
    }
    
    /**
     * 添加白名单中的请求头（大小写不敏感）
     */
    private fun addAllowedHeaders(builder: Request.Builder, originalHeaders: okhttp3.Headers) {
        if (properties.allowedHeaders.isEmpty()) {
            return
        }
        
        val allowedHeadersLowerCase = properties.allowedHeaders.map { it.lowercase() }.toSet()
        
        originalHeaders.names().forEach { headerName ->
            if (allowedHeadersLowerCase.contains(headerName.lowercase())) {
                originalHeaders[headerName]?.let { headerValue ->
                    builder.header(headerName, headerValue)
                }
            }
        }
    }
    
    /**
     * 添加业务认证头和灰度标识
     */
    private fun addAuthHeaders(builder: Request.Builder, userId: String) {
        builder.header(HEADER_BKAPI_AUTH, buildAuthHeader())
            .header(HEADER_DEVOPS_UID, userId)
        
        if (properties.gray.isNotEmpty()) {
            builder.header(HEADER_DEVOPS_GRAY, properties.gray)
        }
    }

    /**
     * 构建 BkApi 认证请求头
     * 使用 JSON 格式包含应用编码和密钥
     */
    private fun buildAuthHeader(): String {
        return "{\"bk_app_code\":\"${properties.bkAppCode}\",\"bk_app_secret\":\"${properties.bkAppSecret}\"}"
    }

    companion object {
        // 请求头常量
        private const val HEADER_BKAPI_AUTH = "X-Bkapi-Authorization"
        private const val HEADER_DEVOPS_UID = "X-DEVOPS-UID"
        private const val HEADER_DEVOPS_GRAY = "X-GATEWAY-TAG"
    }
}