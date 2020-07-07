package com.tencent.bkrepo.docker.auth

import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.docker.constant.AUTH_CHALLENGE_TOKEN
import com.tencent.bkrepo.docker.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.docker.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.docker.response.DockerResponse
import com.tencent.bkrepo.docker.util.JwtUtil
import com.tencent.bkrepo.docker.util.TimeUtil
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * docker auth util
 * @author: owenlxu
 * @date: 2019-11-12
 */
@Service
class AuthUtil {

    fun authUser(request: HttpServletRequest, response: HttpServletResponse): DockerResponse {
        return try {
            val user = DockerClientAuthHandler.extractBasicAuth(request)
            val token = JwtUtil.sign(user.username)
            val issuedAt = TimeUtil.getGMTTime()
            val tokenUrl = String.format(AUTH_CHALLENGE_TOKEN, token, token, issuedAt)
            ResponseEntity.ok().apply {
                header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            }.apply {
                header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
            }.body(tokenUrl)
        } catch (authException: ClientAuthException) {
            logger.warn("Authenticate failed: [$authException]")
            val body = "{\"errors\":[{\"code\":\"UNAUTHORIZED\",\"message\":\"access to the requested resource is not authorized\",\"detail\":[{\"Type\":\"repository\",\"Name\":\"samalba/my-app\",\"Action\":\"pull\"},{\"Type\":\"repository\",\"Name\":\"samalba/my-app\",\"Action\":\"push\"}]}]}"
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).apply {
                header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            }.apply {
                header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
            }.body(body)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthUtil::class.java)
    }
}
