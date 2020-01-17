package com.tencent.bkrepo.docker.auth

import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.auth.AuthCredentials
import com.tencent.bkrepo.common.artifact.auth.BasicAuthCredentials
import com.tencent.bkrepo.common.artifact.auth.ClientAuthHandler
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_RESPONSE_HEADER
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.docker.util.JwtUtil
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import java.lang.Exception
import java.util.Base64
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED
import javax.ws.rs.core.MediaType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class DockerClientAuthHandler(val userResource: ServiceUserResource) : ClientAuthHandler {

    @Value("\${auth.url}")
    private var authUrl: String = ""

    @Value("\${auth.enable}")
    private var authEnable: Boolean = true

    @Autowired
    private lateinit var repositoryResource: RepositoryResource

    @Autowired
    private lateinit var nodeResource: NodeResource

    @Autowired
    private lateinit var artifactConfiguration: ArtifactConfiguration

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: AuthCredentials): String {
//        if (!authEnable ) {
//            return ANONYMOUS_USER
//        }
        val token = (authCredentials as JwtAuthCredentials).token
        val tokenStatu = JwtUtil.verifyToken(token)
        if (tokenStatu == false) {
            throw ClientAuthException("auth failed")
        }
        val userName = JwtUtil.getUserName(token)
        logger.debug("auth token {} ,user {}", token, userName)
        return userName
    }

    override fun onAuthenticateSuccess(userId: String, request: HttpServletRequest) {
        request.setAttribute(USER_KEY, userId)
    }

    override fun onAuthenticateFailed(response: HttpServletResponse, clientAuthException: ClientAuthException) {
        response.status = SC_UNAUTHORIZED
        response.setHeader("Docker-Distribution-Api-Version", "registry/2.0")
        val registryService = "bkrepo"
        val scopeStr = "repository:bkrepo/docker-local/tb:push,pull"
        response.setHeader(BASIC_AUTH_RESPONSE_HEADER, String.format("Bearer realm=\"%s\",service=\"%s\",scope=\"%s\"", authUrl, registryService, scopeStr))
        response.contentType = MediaType.APPLICATION_JSON
        response.getWriter().print(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":\"%s\"}]}", "UNAUTHORIZED", "authentication required", "BAD_CREDENTIAL"))
        response.getWriter().flush()
    }

    override fun extractAuthCredentials(request: HttpServletRequest): AuthCredentials {
        val basicAuthHeader = request.getHeader(BASIC_AUTH_HEADER)
        if (basicAuthHeader.isNullOrBlank()) {
            throw ClientAuthException("Authorization value is null")
        }
        if (!basicAuthHeader.startsWith("Bearer ")) {
            throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
        }
        try {
            val token = basicAuthHeader.removePrefix("Bearer ")
            return JwtAuthCredentials(token)
        } catch (exception: Exception) {
            logger.info("dddddddddddddddddddd")
            throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerClientAuthHandler::class.java)

        fun extractBasicAuth(request: HttpServletRequest): BasicAuthCredentials {
            val basicAuthHeader = request.getHeader(BASIC_AUTH_HEADER)
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
                throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
            }
        }
    }
}

data class JwtAuthCredentials(val token: String) : AuthCredentials()
