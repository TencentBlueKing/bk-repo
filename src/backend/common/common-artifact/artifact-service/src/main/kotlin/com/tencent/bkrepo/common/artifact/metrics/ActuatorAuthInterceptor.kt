package com.tencent.bkrepo.common.artifact.metrics

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.auth.core.AuthService
import com.tencent.bkrepo.common.artifact.config.AUTHORIZATION
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import com.tencent.bkrepo.common.artifact.permission.PermissionService
import com.tencent.bkrepo.common.artifact.permission.PrincipalType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter
import java.util.Base64
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ActuatorAuthInterceptor : HandlerInterceptorAdapter() {

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var permissionService: PermissionService

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val basicAuthHeader = request.getHeader(AUTHORIZATION) ?: throw ClientAuthException()
        try {
            val encodedCredentials = basicAuthHeader.removePrefix(BASIC_AUTH_HEADER_PREFIX)
            val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
            val parts = decodedHeader.split(StringPool.COLON)
            require(parts.size >= 2)
            val userId = authService.checkUserAccount(parts[0], parts[1])
            permissionService.checkPrincipal(userId, PrincipalType.ADMIN)
            return true
        } catch (exception: ClientAuthException) {
            throw exception
        } catch (exception: PermissionCheckException) {
            throw exception
        } catch (exception: Exception) {
            throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme.")
        }
    }

}