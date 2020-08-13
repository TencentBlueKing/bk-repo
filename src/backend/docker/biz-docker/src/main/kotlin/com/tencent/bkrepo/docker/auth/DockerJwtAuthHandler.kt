package com.tencent.bkrepo.docker.auth

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.http.jwt.JwtAuthHandler
import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.docker.constant.AUTH_CHALLENGE_SERVICE_SCOPE
import com.tencent.bkrepo.docker.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.docker.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.docker.constant.ERROR_MESSAGE
import com.tencent.bkrepo.docker.constant.REGISTRY_SERVICE
import org.springframework.beans.factory.annotation.Value
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED
import javax.ws.rs.core.MediaType

/**
 * docker jwt auth handler
 */
class DockerJwtAuthHandler(properties: JwtAuthProperties) : JwtAuthHandler(properties) {

    @Value("\${auth.url}")
    private var authUrl: String = EMPTY

    override fun onAuthenticateFailed(request: HttpServletRequest, response: HttpServletResponse, authenticationException: AuthenticationException) {
        response.status = SC_UNAUTHORIZED
        response.setHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
        val scopeStr = "repository:*/*/tb:push,pull"
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, AUTH_CHALLENGE_SERVICE_SCOPE.format(authUrl, REGISTRY_SERVICE, scopeStr))
        response.contentType = MediaType.APPLICATION_JSON
        response.writer.print(ERROR_MESSAGE.format("UNAUTHORIZED", "authentication required", "BAD_CREDENTIAL"))
        response.writer.flush()
    }
}