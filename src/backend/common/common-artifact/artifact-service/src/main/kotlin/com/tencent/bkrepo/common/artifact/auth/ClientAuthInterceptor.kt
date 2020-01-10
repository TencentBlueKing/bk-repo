package com.tencent.bkrepo.common.artifact.auth

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.APP_KEY
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.annotation.Order
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 依赖源客户端认证拦截器
 *
 * @author: carrypan
 * @date: 2019/11/22
 */
@Order(1)
class ClientAuthInterceptor : HandlerInterceptorAdapter() {

    @Autowired
    private lateinit var clientAuthHandler: ClientAuthHandler

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.getAttribute(USER_KEY) != null || request.getAttribute(APP_KEY) != null) {
            return true
        }
        return try {
            val authCredentials = clientAuthHandler.extractAuthCredentials(request)
            if (authCredentials is AnonymousCredentials) {
                request.setAttribute(USER_KEY, ANONYMOUS_USER)
            } else {
                val userId = clientAuthHandler.onAuthenticate(request, authCredentials)
                logger.info("User[$userId] authenticate success.")
                clientAuthHandler.onAuthenticateSuccess(userId, request)
                request.setAttribute(USER_KEY, userId)
            }
            true
        } catch (authException: ClientAuthException) {
            clientAuthHandler.onAuthenticateFailed(response, authException)
            false
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClientAuthInterceptor::class.java)
    }
}
