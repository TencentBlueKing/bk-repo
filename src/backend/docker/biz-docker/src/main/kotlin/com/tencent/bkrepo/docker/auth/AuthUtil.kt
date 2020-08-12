package com.tencent.bkrepo.docker.auth

import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.common.api.constant.StringPool.COLON
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.auth.basic.BasicAuthCredentials
import com.tencent.bkrepo.common.artifact.auth.jwt.JwtProvider
import com.tencent.bkrepo.common.artifact.config.AUTHORIZATION
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.docker.constant.AUTH_CHALLENGE_TOKEN
import com.tencent.bkrepo.docker.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.docker.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.docker.constant.DOCKER_UNAUTHED_BODY
import com.tencent.bkrepo.docker.response.DockerResponse
import com.tencent.bkrepo.docker.util.TimeUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.util.Base64
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * docker auth util
 * @author: owenlxu
 * @date: 2019-11-12
 */
@Service
class AuthUtil {

    @Value("\${auth.enable}")
    private var authEnable: Boolean = true

    @Autowired
    private lateinit var serviceUserResource: ServiceUserResource

    /**
     * check first docker login user,password
     * check success return bareer token
     * and will carry in other request
     */
    fun authUser(request: HttpServletRequest, response: HttpServletResponse): DockerResponse {
        return try {
            val user = extractBasicAuth(request)
            val claims = mutableMapOf<String, Any>(USER_KEY to user.username)
            val token = JwtProvider.generateToken(user.username, claims)
            val issuedAt = TimeUtil.getGMTTime()
            val tokenUrl = String.format(AUTH_CHALLENGE_TOKEN, token, token, issuedAt)
            ResponseEntity.ok().apply {
                header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            }.apply {
                header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
            }.body(tokenUrl)
        } catch (authException: ClientAuthException) {
            logger.warn("Authenticate failed: [$authException]")
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).apply {
                header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            }.apply {
                header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
            }.body(DOCKER_UNAUTHED_BODY)
        }
    }

    private fun extractBasicAuth(request: HttpServletRequest): BasicAuthCredentials {
        val basicAuthHeader = request.getHeader(AUTHORIZATION)
        try {
            if (basicAuthHeader.isNullOrBlank()) throw ClientAuthException("Authorization value is null")
            if (!basicAuthHeader.startsWith(BASIC_AUTH_HEADER_PREFIX)) throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
            val encodedCredentials = basicAuthHeader.removePrefix(BASIC_AUTH_HEADER_PREFIX)
            val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
            val parts = decodedHeader.split(COLON)
            require(parts.size >= 2)
            if (authEnable) {
                if (!serviceUserResource.checkUserToken(parts[0], parts[1]).data!!) {
                    throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
                }
            }
            return BasicAuthCredentials(parts[0], parts[1])
        } catch (exception: IllegalArgumentException) {
            logger.warn("auth value is not a valid schema")
            throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthUtil::class.java)
    }
}
