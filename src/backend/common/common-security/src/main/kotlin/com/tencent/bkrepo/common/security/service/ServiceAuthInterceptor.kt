package com.tencent.bkrepo.common.security.service

import com.tencent.bkrepo.common.api.constant.MS_REQUEST_KEY
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.security.constant.MS_AUTH_HEADER_SECURITY_TOKEN
import com.tencent.bkrepo.common.security.constant.MS_AUTH_HEADER_UID
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ServiceAuthInterceptor(
    private val serviceAuthManager: ServiceAuthManager,
    private val serviceAuthProperties: ServiceAuthProperties
) : HandlerInterceptorAdapter() {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        // 设置uid
        request.getHeader(MS_AUTH_HEADER_UID)?.let {
            request.setAttribute(USER_KEY, it)
            request.setAttribute(MS_REQUEST_KEY, it)
        }
        // 微服务间jwt认证
        if (serviceAuthProperties.enabled) {
            val securityToken = request.getHeader(MS_AUTH_HEADER_SECURITY_TOKEN).orEmpty()
            serviceAuthManager.verifySecurityToken(securityToken)
        }
        return true
    }
}
