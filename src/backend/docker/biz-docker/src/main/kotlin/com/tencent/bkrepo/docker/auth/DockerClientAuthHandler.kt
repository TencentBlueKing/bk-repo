package com.tencent.bkrepo.docker.auth

import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.common.api.constant.APP_KEY
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.auth.basic.BasicAuthCredentials
import com.tencent.bkrepo.common.artifact.auth.core.AuthCredentials
import com.tencent.bkrepo.common.artifact.auth.core.AuthService
import com.tencent.bkrepo.common.artifact.auth.core.ClientAuthHandler
import com.tencent.bkrepo.common.artifact.auth.platform.PlatformAuthCredentials
import com.tencent.bkrepo.common.artifact.config.AUTHORIZATION
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_RESPONSE_HEADER
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
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

@Component
class DockerClientAuthHandler(val userResource: ServiceUserResource) :
    ClientAuthHandler {

    @Value("\${auth.url}")
    private var authUrl: String = ""

    @Value("\${auth.enable}")
    private var authEnable: Boolean = true

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var serviceUserResource: ServiceUserResource

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
            logger.info("auth token failed {} ", token)
            throw ClientAuthException("auth failed")
        }
        val userName = JwtUtil.getUserName(token)
        logger.info("auth token {} ,user {}", token, userName)
        return userName
    }

    private fun checkUserId(userId: String) {
        if (serviceUserResource.detail(userId).data == null) {
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
        response.setHeader("Docker-Distribution-Api-Version", "registry/2.0")
        val registryService = "bkrepo"
        val scopeStr = "repository:bkrepo/docker-local/tb:push,pull"
        response.setHeader(
            BASIC_AUTH_RESPONSE_HEADER,
            String.format("Bearer realm=\"%s\",service=\"%s\",scope=\"%s\"", authUrl, registryService, scopeStr)
        )
        response.contentType = MediaType.APPLICATION_JSON
        response.writer.print(
            String.format(
                "{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":\"%s\"}]}",
                "UNAUTHORIZED",
                "authentication required",
                "BAD_CREDENTIAL"
            )
        )
        response.writer.flush()
    }

    override fun extractAuthCredentials(request: HttpServletRequest): AuthCredentials {
        val basicAuthHeader = request.getHeader(AUTHORIZATION)
        if (request.requestURI.startsWith(USER_API_PREFIX)) {
            val encodedCredentials = basicAuthHeader.removePrefix(PLATFORM_AUTH_HEADER_PREFIX)
            val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
            val parts = decodedHeader.split(":")
            require(parts.size >= 2)
            return PlatformAuthCredentials(parts[0], parts[1])
        }
        if (basicAuthHeader.isNullOrBlank()) {
            logger.warn("auth value is null and path is {}", request.requestURI)
            throw ClientAuthException("Authorization value is null")
        }
        if (!basicAuthHeader.startsWith("Bearer ")) {
            logger.warn("parse token failed {}", basicAuthHeader)
            throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
        }
        try {
            val token = basicAuthHeader.removePrefix("Bearer ")
            return JwtAuthCredentials(token)
        } catch (exception: Exception) {
            logger.warn("Authorization value [$basicAuthHeader] is not a valid scheme")
            throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
        }
    }

    companion object {

        private const val PLATFORM_AUTH_HEADER_PREFIX = "Platform "

        private val logger = LoggerFactory.getLogger(DockerClientAuthHandler::class.java)

        fun extractBasicAuth(request: HttpServletRequest): BasicAuthCredentials {
            val basicAuthHeader = request.getHeader(AUTHORIZATION)
            if (basicAuthHeader.isNullOrBlank()) throw ClientAuthException("Authorization value is null")
            if (!basicAuthHeader.startsWith(BASIC_AUTH_HEADER_PREFIX)) throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")

            try {
                val encodedCredentials = basicAuthHeader.removePrefix(BASIC_AUTH_HEADER_PREFIX)
                val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
                val parts = decodedHeader.split(":")
                require(parts.size >= 2)
                return BasicAuthCredentials(
                    parts[0],
                    parts[1]
                )
            } catch (exception: Exception) {
                logger.warn("auth value is not a valid schema")
                throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
            }
        }
    }
}

data class JwtAuthCredentials(val token: String) : AuthCredentials()
