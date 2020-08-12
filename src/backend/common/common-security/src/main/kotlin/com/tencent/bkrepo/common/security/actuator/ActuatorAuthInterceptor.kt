package com.tencent.bkrepo.common.security.actuator

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.security.constant.BASIC_AUTH_PREFIX
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.BadCredentialsException
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.permission.PrincipalType
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter
import java.util.Base64
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ActuatorAuthInterceptor(
    private val authenticationManager: AuthenticationManager,
    private val permissionManager: PermissionManager
) : HandlerInterceptorAdapter() {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION) ?: throw AuthenticationException()
        try {
            val encodedCredentials = authorizationHeader.removePrefix(BASIC_AUTH_PREFIX)
            val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
            val parts = decodedHeader.split(StringPool.COLON)
            require(parts.size >= 2)
            val userId = authenticationManager.checkUserAccount(parts[0], parts[1])
            permissionManager.checkPrincipal(userId, PrincipalType.ADMIN)
            return true
        } catch (exception: AuthenticationException) {
            throw exception
        } catch (exception: PermissionException) {
            throw exception
        } catch (exception: IllegalArgumentException) {
            throw BadCredentialsException("Authorization value [$authorizationHeader] is not a valid scheme.")
        }
    }
}
