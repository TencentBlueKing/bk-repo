package com.tencent.bkrepo.oci.artifact.auth

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.security.constant.BASIC_AUTH_PREFIX
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.http.basic.BasicAuthCredentials
import com.tencent.bkrepo.common.security.http.core.HttpAuthHandler
import com.tencent.bkrepo.common.security.http.credentials.AnonymousCredentials
import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.common.security.util.BasicAuthUtils
import com.tencent.bkrepo.oci.pojo.response.AuthenticateResponse
import com.tencent.bkrepo.oci.pojo.response.HelmResponse
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * helm registry login handler
 */
class OciLoginAuthHandler(
    private val authenticationManager: AuthenticationManager
) : HttpAuthHandler {

    /**
     * login to a registry (with manual password entry)
     */
    override fun getLoginEndpoint() = "/{projectId}/{repoName}/v2"
    override fun getLoginMethod() = HttpMethod.GET.name

    override fun extractAuthCredentials(request: HttpServletRequest): HttpAuthCredentials {
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION).orEmpty()
        return if (authorizationHeader.startsWith(BASIC_AUTH_PREFIX)) {
            try {
                val pair = BasicAuthUtils.decode(authorizationHeader)
                BasicAuthCredentials(pair.first, pair.second)
            } catch (ignored: IllegalArgumentException) {
                throw AuthenticationException("Invalid authorization value.")
            }
        } else AnonymousCredentials()
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: HttpAuthCredentials): String {
        require(authCredentials is BasicAuthCredentials)
        return authenticationManager.checkUserAccount(authCredentials.username, authCredentials.password)
    }

    override fun onAuthenticateSuccess(request: HttpServletRequest, response: HttpServletResponse, userId: String) {
        response.status = HttpStatus.OK.value
        response.contentType = MediaType.APPLICATION_JSON_VALUE
    }

    override fun onAuthenticateFailed(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authenticationException: AuthenticationException
    ) {
        response.status = HttpStatus.UNAUTHORIZED.value
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val helmResponse = HelmResponse.unAuthenticated(
            listOf(AuthenticateResponse.unAuthenticated("UNAUTHORIZED", "authentication required", null))
        )
        response.writer.write(helmResponse.toJsonString())
    }
}
