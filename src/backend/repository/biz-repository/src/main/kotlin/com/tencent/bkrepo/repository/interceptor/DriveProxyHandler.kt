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
     * 保留原始请求的所有请求头，但移除 Host 头（避免路由错误），并添加业务认证头
     */
    override fun before(
        proxyRequest: HttpServletRequest,
        proxyResponse: HttpServletResponse,
        request: Request
    ): Request {
        val userId = SecurityUtils.getUserId()

        return request.newBuilder()
            .removeHeader(HEADER_HOST)
            .header(HEADER_BKAPI_AUTH, buildAuthHeader())
            .header(HEADER_DEVOPS_UID, userId)
            .apply {
                // 添加灰度标识（如果配置）
                if (properties.gray.isNotEmpty()) {
                    header(HEADER_DEVOPS_GRAY, properties.gray)
                }
            }
            .build()
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
        private const val HEADER_HOST = "Host"
        private const val HEADER_BKAPI_AUTH = "X-Bkapi-Authorization"
        private const val HEADER_DEVOPS_UID = "X-DEVOPS-UID"
        private const val HEADER_DEVOPS_GRAY = "X-GATEWAY-TAG"
    }
}