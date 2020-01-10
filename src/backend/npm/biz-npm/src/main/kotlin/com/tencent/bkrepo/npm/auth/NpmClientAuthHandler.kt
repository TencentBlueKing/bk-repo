package com.tencent.bkrepo.npm.auth

import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.auth.AuthCredentials
import com.tencent.bkrepo.common.artifact.auth.ClientAuthHandler
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import com.tencent.bkrepo.npm.exception.NpmClientAuthException
import com.tencent.bkrepo.npm.utils.JwtUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest

@Component
class NpmClientAuthHandler : ClientAuthHandler {

    @Autowired
    private lateinit var serviceUserResource: ServiceUserResource

    override fun extractAuthCredentials(request: HttpServletRequest): AuthCredentials {
        val bearerAuthHeader = request.getHeader(BEARER_AUTH_HEADER)
        if (bearerAuthHeader.isNullOrBlank()) throw NpmClientAuthException("Authorization value is null")
        if (!bearerAuthHeader.startsWith(BEARER_AUTH_HEADER_PREFIX)) throw NpmClientAuthException("Authorization value [$bearerAuthHeader] is not a valid scheme")

        return try {
            val token = bearerAuthHeader.removePrefix(BEARER_AUTH_HEADER_PREFIX)
            JwtAuthCredentials(token)
        } catch (e: Exception) {
            throw ClientAuthException("Authorization value [$bearerAuthHeader] is not a valid scheme")
        }
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: AuthCredentials): String {
        with(authCredentials as JwtAuthCredentials) {
            // val username = JwtUtils.getUserName(token)
            // return if (JwtUtils.verifyToken(token)) {
            //     username
            // } else {
            //     throw ClientAuthException("Authorization value check failed.")
            // }
            val username = JwtUtils.getUserName(token)
            val password = JwtUtils.getPassword(token)
            val response = serviceUserResource.checkUserToken(username, password)
            if (response.data == true) {
                return username
            } else {
                throw PermissionCheckException("Access Forbidden.")
            }
        }
    }

    override fun onAuthenticateSuccess(userId: String, request: HttpServletRequest) {
        request.setAttribute(USER_KEY, userId)
    }
}

data class JwtAuthCredentials(val token: String) : AuthCredentials()