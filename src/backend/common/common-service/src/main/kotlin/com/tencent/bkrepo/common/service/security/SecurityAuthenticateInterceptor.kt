package com.tencent.bkrepo.common.service.security

import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_SECURITY_TOKEN
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class SecurityAuthenticateInterceptor : HandlerInterceptorAdapter() {

    @Autowired
    private lateinit var securityAuthenticateManager: SecurityAuthenticateManager

    @Autowired
    private lateinit var securityProperties: SecurityProperties

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val securityToken = request.getHeader(MS_AUTH_HEADER_SECURITY_TOKEN).orEmpty()
        if (securityProperties.enabled) {
            try {
                securityAuthenticateManager.verifySecurityToken(securityToken)
            } catch (exception: ErrorCodeException) {
                println("message: " + exception.message)
                throw exception
            }
        }
        return true
    }
}
