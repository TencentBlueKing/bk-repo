package com.tencent.bkrepo.common.security.service

import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_SECURITY_TOKEN
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ServiceAuthInterceptor(
    private val serviceAuthManager: ServiceAuthManager,
    private val serviceAuthProperties: ServiceAuthProperties
) : HandlerInterceptorAdapter() {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val securityToken = request.getHeader(MS_AUTH_HEADER_SECURITY_TOKEN).orEmpty()
        if (serviceAuthProperties.enabled) {
            serviceAuthManager.verifySecurityToken(securityToken)
        }
        return true
    }
}
