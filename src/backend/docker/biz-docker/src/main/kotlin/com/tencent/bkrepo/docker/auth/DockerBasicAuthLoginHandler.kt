package com.tencent.bkrepo.docker.auth

import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.http.basic.BasicAuthHandler
import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.common.security.util.JwtUtils
import com.tencent.bkrepo.docker.constant.AUTH_CHALLENGE_TOKEN
import com.tencent.bkrepo.docker.constant.DOCKER_API_SUFFIX
import com.tencent.bkrepo.docker.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.docker.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.docker.constant.DOCKER_UNAUTHED_BODY
import com.tencent.bkrepo.docker.util.TimeUtil
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * docker basic auth logon handler
 */
class DockerBasicAuthLoginHandler(
    private val properties: JwtAuthProperties,
    authenticationManager: AuthenticationManager
): BasicAuthHandler(authenticationManager) {

    private val signingKey = JwtUtils.createSigningKey(properties.secretKey)

    override fun getLoginEndpoint() = DOCKER_API_SUFFIX

    override fun onAuthenticateSuccess(request: HttpServletRequest, response: HttpServletResponse, userId: String) {
        val token = JwtUtils.generateToken(signingKey, properties.expiration, userId)
        val issuedAt = TimeUtil.getGMTTime()
        val tokenUrl = AUTH_CHALLENGE_TOKEN.format(token, token, issuedAt)
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.setHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
        response.writer.print(tokenUrl)
        response.writer.flush()
        super.onAuthenticateSuccess(request, response, userId)
    }

    override fun onAuthenticateFailed(request: HttpServletRequest, response: HttpServletResponse, authenticationException: AuthenticationException) {
        logger.warn("Authenticate failed: [$authenticationException]")
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.setHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
        response.writer.print(DOCKER_UNAUTHED_BODY)
        response.writer.flush()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerBasicAuthLoginHandler::class.java)
    }
}