package com.tencent.bkrepo.docker.auth

import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.docker.response.DockerResponse
import com.tencent.bkrepo.docker.util.JwtUtil
import com.tencent.bkrepo.docker.util.TimeUtil
import org.slf4j.LoggerFactory
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
            val tokenUrl = String.format("{\"token\": \"%s\", \"access_token\": \"%s\",\"issued_at\": \"%s\"}", token, token, issuedAt)
            ResponseEntity.ok().apply {
                header("Content-Type", "application/json")
            }.apply {
                header("Docker-Distribution-Api-Version", "registry/2.0")
            }.body(tokenUrl)
        } catch (authException: ClientAuthException) {
            logger.warn("Authenticate failed: [$authException]")
            val body = "{\"errors\":[{\"code\":\"UNAUTHORIZED\",\"message\":\"access to the requested resource is not authorized\",\"detail\":[{\"Type\":\"repository\",\"Name\":\"samalba/my-app\",\"Action\":\"pull\"},{\"Type\":\"repository\",\"Name\":\"samalba/my-app\",\"Action\":\"push\"}]}]}"
            ResponseEntity.status(401).apply {
                header("Content-Type", "application/json")
            }.apply {
                header("Docker-Distribution-Api-Version", "registry/2.0")
            }.body(body)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthUtil::class.java)
    }
}
