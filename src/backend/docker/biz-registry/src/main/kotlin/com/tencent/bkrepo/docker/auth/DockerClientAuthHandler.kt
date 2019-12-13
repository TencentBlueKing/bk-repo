package com.tencent.bkrepo.docker.auth

import com.tencent.bkrepo.common.artifact.auth.ClientAuthHandler
import com.tencent.bkrepo.common.artifact.auth.DefaultClientAuthHandler
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_RESPONSE_HEADER
import com.tencent.bkrepo.common.artifact.config.REPO_KEY
import com.tencent.bkrepo.common.artifact.config.USER_KEY
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.docker.util.JwtUtil
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.auth.api.ServiceUserResource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.lang.Exception
import java.util.Base64
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED
import javax.ws.rs.core.MediaType


@Component
class DockerClientAuthHandler(val userResource: ServiceUserResource) : ClientAuthHandler {

    @Autowired
    private lateinit var repositoryResource: RepositoryResource

    @Autowired
    private lateinit var nodeResource: NodeResource

    @Autowired
    private lateinit var artifactConfiguration: ArtifactConfiguration

    override fun needAuthenticate(uri: String, projectId: String?, repoName: String?): Boolean {
        if (projectId.isNullOrEmpty() || repoName.isNullOrEmpty()) {
            logger.debug("Can not extract projectId or repoName")
            return true
        }
        val typeName = artifactConfiguration.getRepositoryType()?.name ?: ""
        val response = repositoryResource.detail(projectId, repoName, typeName)
        if (response.isNotOk()) {
            logger.warn("Query repository detail failed: [$response]")
            return true
        }
        val repo = response.data
        if (repo == null) {
            logger.warn("Repository $projectId/$repoName($typeName) dose not exist.")
            return true
        }
        val requestAttributes = RequestContextHolder.getRequestAttributes() as ServletRequestAttributes
        requestAttributes.request.setAttribute(REPO_KEY, repo)
        return !repo.public
    }

    override fun onAuthenticate(request: HttpServletRequest): String {
        val token = extractBasicAuth(request)
        val userName = JwtUtil.getUserName(token)
        val password = JwtUtil.getPassword(token)
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
        val tokenUrl = "http://registry.me:8002/v2/auth"
        val registryService = "bkrepo"
        val scopeStr = ""
        response.setHeader(BASIC_AUTH_RESPONSE_HEADER, String.format("Bearer realm=\"%s\",service=\"%s\"", tokenUrl, registryService) + scopeStr)
        response.contentType = MediaType.APPLICATION_JSON
        response.getWriter().print(String.format("{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":\"%s\"}]}", "UNAUTHORIZED", "authentication required", "BAD_CREDENTIAL"))
        response.getWriter().flush()
    }

    private fun extractBasicAuth(request: HttpServletRequest): String {
        val basicAuthHeader = request.getHeader(BASIC_AUTH_HEADER)
        if (basicAuthHeader.isNullOrBlank()) throw ClientAuthException("Authorization value is null")
        if (!basicAuthHeader.startsWith("Bearer ")) throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")

        try {
            val token = basicAuthHeader.removePrefix("Bearer ")
            return token
        } catch (exception: Exception) {
            throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerClientAuthHandler::class.java)

        fun extractBasicAuth(request: HttpServletRequest): DefaultClientAuthHandler.BasicAuthCredentials {
            val basicAuthHeader = request.getHeader(BASIC_AUTH_HEADER)
            if (basicAuthHeader.isNullOrBlank()) throw ClientAuthException("Authorization value is null")
            if (!basicAuthHeader.startsWith(BASIC_AUTH_HEADER_PREFIX)) throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")

            try {
                val encodedCredentials = basicAuthHeader.removePrefix(BASIC_AUTH_HEADER_PREFIX)
                val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
                val parts = decodedHeader.split(":")
                require(parts.size >= 2)
                return DefaultClientAuthHandler.BasicAuthCredentials(
                    parts[0],
                    parts[1]
                )
            } catch (exception: Exception) {
                throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
            }
        }

    }

}
