package com.tencent.bkrepo.docker.auth

import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.docker.util.JwtUtil
import com.tencent.bkrepo.docker.util.TimeUtils
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class AuthUtil {

    fun authUser(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Any> {
        try {
            val user = DockerClientAuthHandler.extractBasicAuth(request)
            val token = JwtUtil.sign(user.username, user.password)
            val issuedAt = TimeUtils.getGMTTime()
            val tokenUrl = String.format("{\"token\": \"%s\", \"expires_in\": 3600,\"issued_at\": \"%s\"}", token, issuedAt)
            return ResponseEntity.ok().header("Content-Type", "application/json").header("Docker-Distribution-Api-Version", "registry/2.0").body(tokenUrl)
        } catch (authException: ClientAuthException) {
            logger.warn("Authenticate failed: $authException")
            val body = "{\"errors\":[{\"code\":\"UNAUTHORIZED\",\"message\":\"access to the requested resource is not authorized\",\"detail\":[{\"Type\":\"repository\",\"Name\":\"samalba/my-app\",\"Action\":\"pull\"},{\"Type\":\"repository\",\"Name\":\"samalba/my-app\",\"Action\":\"push\"}]}]}"
            return ResponseEntity.status(401).header("Content-Type", "application/json").header("Docker-Distribution-Api-Version", "registry/2.0").body(body)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthUtil::class.java)
    }
}
