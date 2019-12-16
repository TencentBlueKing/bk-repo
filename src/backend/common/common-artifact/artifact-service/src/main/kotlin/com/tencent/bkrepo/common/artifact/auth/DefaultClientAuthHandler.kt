package com.tencent.bkrepo.common.artifact.auth

import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.config.REPO_KEY
import com.tencent.bkrepo.common.artifact.config.USER_KEY
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.repository.api.RepositoryResource
import java.util.Base64
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
open class DefaultClientAuthHandler : ClientAuthHandler {

    @Autowired
    private lateinit var repositoryResource: RepositoryResource

    @Autowired
    private lateinit var artifactConfiguration: ArtifactConfiguration

    override fun needAuthenticate(uri: String, projectId: String?, repoName: String?): Boolean {
        if (projectId.isNullOrEmpty() || repoName.isNullOrEmpty()) {
            logger.debug("ProjectId or repoName is missing, default to authenticate.")
            return true
        }
        val typeName = artifactConfiguration.getRepositoryType()?.name ?: ""
        val response = repositoryResource.detail(projectId, repoName, typeName)
        val repo = response.data ?: throw ArtifactNotFoundException("Repository[$repoName] does not exist")
        val requestAttributes = RequestContextHolder.getRequestAttributes() as ServletRequestAttributes
        requestAttributes.request.setAttribute(REPO_KEY, repo)
        return !repo.public
    }

    override fun onAuthenticate(request: HttpServletRequest): String {
        val userId = request.getHeader(AUTH_HEADER_USER_ID)
        //  TODO: header方式传递进来的直接通过
        if (userId != null) {
            logger.debug("Extract userId from header: $userId.")
            return userId
        }
        val credentials = extractBasicAuth(request)
        logger.debug("Extract userId from BasicAuth header: [${credentials.username}].")
        // TODO: auth 进行认证
        return credentials.username
    }

    override fun onAuthenticateFailed(response: HttpServletResponse, clientAuthException: ClientAuthException) {
        // 默认向上抛异常，由ArtifactExceptionHandler统一处理
        throw clientAuthException
    }

    override fun onAuthenticateSuccess(userId: String, request: HttpServletRequest) {
        request.setAttribute(USER_KEY, userId)
    }

    private fun extractBasicAuth(request: HttpServletRequest): BasicAuthCredentials {
        val basicAuthHeader = request.getHeader(BASIC_AUTH_HEADER)
        if (basicAuthHeader.isNullOrBlank()) throw ClientAuthException("Missing Authorization value")
        if (!basicAuthHeader.startsWith(BASIC_AUTH_HEADER_PREFIX)) throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")

        try {
            val encodedCredentials = basicAuthHeader.removePrefix(BASIC_AUTH_HEADER_PREFIX)
            val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
            val parts = decodedHeader.split(":")
            require(parts.size >= 2)
            return BasicAuthCredentials(parts[0], parts[1])
        } catch (exception: Exception) {
            throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultClientAuthHandler::class.java)
    }

    data class BasicAuthCredentials(val username: String, val password: String)
}
