package com.tencent.bkrepo.docker.auth

import com.tencent.bkrepo.common.artifact.auth.ClientAuthHandler
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_RESPONSE_HEADER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.docker.util.JwtUtil
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.artifact.auth.AuthCredentials
import com.tencent.bkrepo.common.artifact.auth.BasicAuthCredentials
import com.tencent.bkrepo.docker.constant.AUTH_DISABLE
import com.tencent.bkrepo.docker.constant.AUTH_ENABLE
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.lang.Exception
import java.util.Base64
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED
import javax.ws.rs.core.MediaType


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
        val userName = JwtUtil.getUserName(token)
        val password = JwtUtil.getPassword(token)
        logger.debug("auth token {} ,user {}, password {}",token, userName, password)
        val result = userResource.checkUserToken(userName, password)
        if (result.data == false) {
            throw  ClientAuthException("auth failed")
        }
        return userName
    }

    override fun onAuthenticateSuccess(userId: String, request: HttpServletRequest) {
        request.setAttribute(USER_KEY, userId)
    }

    override fun onAuthenticateFailed(response: HttpServletResponse, clientAuthException: ClientAuthException) {
        response.status = SC_UNAUTHORIZED
        response.setHeader("Docker-Distribution-Api-Version", "registry/2.0")
        val registryService = "bkrepo"
        val scopeStr = "repository:samalba/my-app:pull,push"
        response.setHeader(BASIC_AUTH_RESPONSE_HEADER, String.format("Bearer realm=\"%s\",service=\"%s\",scope=\"%s\"", authUrl, registryService,scopeStr) )
        response.contentType = MediaType.APPLICATION_JSON
        logger.info("aaaaaaaaaaaaaaaaaaaaaa")
        response.getWriter().print(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":\"%s\"}]}", "UNAUTHORIZED", "authentication required", "BAD_CREDENTIAL"))
        response.getWriter().flush()
    }


    override fun extractAuthCredentials(request: HttpServletRequest): AuthCredentials {
//        if (!authEnable) {
//            return JwtAuthCredentials(ANONYMOUS_USER)
//        }
        val basicAuthHeader = request.getHeader(BASIC_AUTH_HEADER)
        logger.info("eeeeeeeeeeeeeee {} , {}, {}", basicAuthHeader, request.requestURI, request.method)
        if (basicAuthHeader.isNullOrBlank()) {
            logger.info("bbbbbbbbbbbbbbbbbbb")
            throw ClientAuthException("Authorization value is null")
        }
        if (!basicAuthHeader.startsWith("Bearer ")){
            logger.info("cccccccccccccccccccccc")
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
