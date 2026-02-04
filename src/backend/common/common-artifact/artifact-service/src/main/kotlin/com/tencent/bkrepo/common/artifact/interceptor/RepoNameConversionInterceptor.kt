package com.tencent.bkrepo.common.artifact.interceptor

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

/**
 * repoName转换拦截器
 * 针对X-DEVOPS-CHANNEL=CREATIVE_STREAM的请求，将repoName转换为creative
 */
class RepoNameConversionInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val channelHeader = request.getHeader(CHANNEL_HEADER)
        if (channelHeader != CREATIVE_STREAM_VALUE) {
            return true
        }

        // 转换URL路径中的repoName
        val pathVariables = request.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
        ) as? MutableMap<String, String>

        if (pathVariables?.containsKey("repoName") == true) {
            logger.info("Converting repoName in path from [${pathVariables["repoName"]}] to [$CREATIVE]")
            pathVariables["repoName"] = CREATIVE
        }

        // 对于POST/PUT/PATCH请求，转换请求体中的repoName
        if (shouldConvertRequestBody(request)) {
            wrapRequestForBodyConversion(request)
        }

        return true
    }

    private fun shouldConvertRequestBody(request: HttpServletRequest): Boolean {
        val method = request.method
        return (method == HttpMethod.POST.name() ||
            method == HttpMethod.PUT.name() ||
            method == HttpMethod.PATCH.name()) &&
            request.contentType?.contains("application/json") == true
    }

    /**
     * 标记需要转换请求体，实际转换由RequestBodyAdvice完成
     */
    private fun wrapRequestForBodyConversion(request: HttpServletRequest) {
        request.setAttribute(NEED_CONVERSION_ATTRIBUTE, true)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RepoNameConversionInterceptor::class.java)
        private const val CHANNEL_HEADER = "X-DEVOPS-CHANNEL"
        private const val CREATIVE_STREAM_VALUE = "CREATIVE_STREAM"
        const val CREATIVE = "creative"
        const val NEED_CONVERSION_ATTRIBUTE = "NEED_REPO_NAME_CONVERSION"
    }
}
