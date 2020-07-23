package com.tencent.bkrepo.docker.auth

import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.auth.constant.PLATFORM_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.common.api.constant.APP_KEY
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.StringPool.COLON
import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.auth.core.AuthCredentials
import com.tencent.bkrepo.common.artifact.auth.core.AuthService
import com.tencent.bkrepo.common.artifact.auth.core.ClientAuthHandler
import com.tencent.bkrepo.common.artifact.auth.platform.PlatformAuthCredentials
import com.tencent.bkrepo.common.artifact.config.AUTHORIZATION
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_RESPONSE_HEADER
import com.tencent.bkrepo.common.artifact.config.BEARER_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.docker.constant.AUTH_CHALLENGE_SERVICE_SCOPE
import com.tencent.bkrepo.docker.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.docker.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.docker.constant.ERROR_MESSAGE
import com.tencent.bkrepo.docker.constant.REGISTRY_SERVICE
import com.tencent.bkrepo.docker.constant.USER_API_PREFIX
import com.tencent.bkrepo.docker.util.JwtUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Base64
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED
import javax.ws.rs.core.MediaType

/**
 * docker auth handler
 * to define auth method
 * @author: owenlxu
 * @date: 2019-11-12
 */
@Component
class DockerClientAuthHandler : ClientAuthHandler {

    @Value("\${auth.url}")
    private var authUrl: String = EMPTY

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var serviceUserResource: ServiceUserResource

    /**
     * check api request ,not from docker client
     * if platform passed, create user if not exist
     */
    override fun onAuthenticate(request: HttpServletRequest, authCredentials: AuthCredentials): String {
        if (request.requestURI.startsWith(USER_API_PREFIX)) {
            with(authCredentials as PlatformAuthCredentials) {
                val appId = authService.checkPlatformAccount(accessKey, secretKey)
                val userId = request.getHeader(AUTH_HEADER_UID)?.let {
                    checkUserId(it)
                    it
                } ?: appId
                request.setAttribute(APP_KEY, appId)
                request.setAttribute(USER_KEY, userId)
                return userId
            }
        }
        val token = (authCredentials as JwtAuthCredentials).token
        if (!JwtUtil.verifyToken(token)) {
            logger.warn("auth token failed [$token] ")
            throw ClientAuthException("auth failed")
        }
        val userName = JwtUtil.getUserName(token)
        logger.info("auth token [$token] ,user [$userName]")
        return userName
    }

    private fun checkUserId(userId: String) {
        serviceUserResource.detail(userId).data ?: run {
            val request = CreateUserRequest(userId = userId, name = userId)
            serviceUserResource.createUser(request)
            logger.info("create user request: [$request] success")
        }
    }

    override fun onAuthenticateSuccess(userId: String, request: HttpServletRequest) {
        request.setAttribute(USER_KEY, userId)
    }

    override fun onAuthenticateFailed(response: HttpServletResponse, clientAuthException: ClientAuthException) {
        response.status = SC_UNAUTHORIZED
        response.setHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
        val scopeStr = "repository:*/*/tb:push,pull"
        response.setHeader(
            BASIC_AUTH_RESPONSE_HEADER,
            String.format(AUTH_CHALLENGE_SERVICE_SCOPE, authUrl, REGISTRY_SERVICE, scopeStr)
        )
        response.contentType = MediaType.APPLICATION_JSON
        response.writer.print(
            String.format(ERROR_MESSAGE, "UNAUTHORIZED", "authentication required", "BAD_CREDENTIAL")
        )
        response.writer.flush()
    }

    override fun extractAuthCredentials(request: HttpServletRequest): AuthCredentials {
        val basicAuthHeader = request.getHeader(AUTHORIZATION)
        if (request.requestURI.startsWith(USER_API_PREFIX)) {
            val encodedCredentials = basicAuthHeader.removePrefix(PLATFORM_AUTH_HEADER_PREFIX)
            val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
            val parts = decodedHeader.split(COLON)
            require(parts.size >= 2)
            return PlatformAuthCredentials(parts[0], parts[1])
        }
        if (basicAuthHeader.isNullOrBlank()) {
            logger.warn("auth value is null and path is [${request.requestURI}]")
            throw ClientAuthException("Authorization value is null")
        }
        if (!basicAuthHeader.startsWith(BEARER_AUTH_HEADER_PREFIX)) {
            logger.warn("parse token failed [$basicAuthHeader]")
            throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
        }
        val token = basicAuthHeader.removePrefix(BEARER_AUTH_HEADER_PREFIX)
        return JwtAuthCredentials(token)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerClientAuthHandler::class.java)
    }
}

data class JwtAuthCredentials(val token: String) : AuthCredentials()
