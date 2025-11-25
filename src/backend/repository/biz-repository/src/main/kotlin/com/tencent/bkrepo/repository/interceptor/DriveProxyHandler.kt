package com.tencent.bkrepo.repository.interceptor

import com.tencent.bkrepo.common.service.util.proxy.DefaultProxyCallHandler
import com.tencent.bkrepo.repository.config.DriveProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory

/**
 * BkDrive 代理请求处理器
 * 负责在请求转发前后添加必要的请求头和处理响应
 */
class DriveProxyHandler(
    private val properties: DriveProperties
) : DefaultProxyCallHandler() {
    
    /**
     * 请求转发前的处理
     * 添加 CI 服务所需的认证和标识请求头
     */
    override fun before(
        proxyRequest: HttpServletRequest, 
        proxyResponse: HttpServletResponse, 
        request: Request
    ): Request {
        val userId = proxyRequest.getAttribute("userId") as String

        logger.debug("Preparing proxy request for user: {}, url: {}", userId, request.url)
        
        return request.newBuilder()
            // 添加必需的认证请求头
            .header(HEADER_DEVOPS_TOKEN, properties.ciToken)
            .header(HEADER_DEVOPS_UID, userId)
            .apply {
                // 添加灰度标识（如果配置了）
                if (properties.gray.isNotEmpty()) {
                    header(HEADER_DEVOPS_GRAY, properties.gray)
                }
            }
            .build()
    }

    /**
     * 请求转发后的处理
     * 使用默认实现，转发响应状态码、响应头和响应体
     */
    override fun after(
        proxyRequest: HttpServletRequest, 
        proxyResponse: HttpServletResponse, 
        response: Response
    ) {
        logger.debug("Proxy response received: status=${response.code}, url=${response.request.url}")
        super.after(proxyRequest, proxyResponse, response)
    }
    
    companion object {
        private val logger = LoggerFactory.getLogger(DriveProxyHandler::class.java)
        
        // CI 服务请求头常量
        private const val HEADER_DEVOPS_TOKEN = "X-DEVOPS-BK-TOKEN"
        private const val HEADER_DEVOPS_UID = "X-DEVOPS-UID"
        private const val HEADER_DEVOPS_GRAY = "X-GATEWAY-TAG"
    }
}