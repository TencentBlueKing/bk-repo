package com.tencent.bkrepo.auth.interceptor

import com.tencent.bkrepo.auth.constant.AUTHORIZATION
import com.tencent.bkrepo.auth.constant.PLATFORM_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_FAILED_RESPONSE
import com.tencent.bkrepo.auth.service.AccountService
import com.tencent.bkrepo.common.api.constant.StringPool
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.Base64

class AuthInterceptor : HandlerInterceptor {

    @Autowired
    private lateinit var accountService: AccountService

    @Throws(Exception::class)
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val basicAuthHeader = request.getHeader(AUTHORIZATION).orEmpty()
        val authFailStr = String.format(AUTH_FAILED_RESPONSE, basicAuthHeader)
        if (basicAuthHeader.startsWith(PLATFORM_AUTH_HEADER_PREFIX)) {
            try {
                val encodedCredentials = basicAuthHeader.removePrefix(PLATFORM_AUTH_HEADER_PREFIX)
                val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
                val parts = decodedHeader.split(StringPool.COLON)
                require(parts.size == 2)
                accountService.checkCredential(parts[0], parts[1]) ?: run {
                    response.getWriter().print(authFailStr)
                    return false
                }
                return true
            } catch (e: Exception) {
                response.getWriter().print(authFailStr)
            }
        } else {
            response.getWriter().print(authFailStr)
        }
        return false
    }

    @Throws(Exception::class)
    override fun postHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        modelAndView: ModelAndView?
    ) {
    }

    @Throws(Exception::class)
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
    }
}
