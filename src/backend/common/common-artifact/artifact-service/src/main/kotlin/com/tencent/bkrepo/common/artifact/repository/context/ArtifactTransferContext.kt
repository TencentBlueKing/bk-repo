package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.config.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.config.REPO_KEY
import com.tencent.bkrepo.common.artifact.config.USER_KEY
import com.tencent.bkrepo.common.storage.core.ClientCredentials
import com.tencent.bkrepo.common.storage.util.CredentialsUtils
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.repository.pojo.repo.configuration.RepositoryConfiguration
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * 构件传输context
 * @author: carrypan
 * @date: 2019/11/26
 */
open class ArtifactTransferContext {
    val request: HttpServletRequest
    val response: HttpServletResponse
    val userId: String
    val repositoryInfo: RepositoryInfo
    val artifactInfo: ArtifactInfo
    val storageCredentials: ClientCredentials?
    val repositoryConfiguration: RepositoryConfiguration
    val contextAttributes: MutableMap<String, Any>

    init {
        val requestAttributes = RequestContextHolder.getRequestAttributes() as ServletRequestAttributes
        request = requestAttributes.request
        response = requestAttributes.response!!
        userId = request.getAttribute(USER_KEY) as String
        repositoryInfo = request.getAttribute(REPO_KEY) as RepositoryInfo
        artifactInfo = request.getAttribute(ARTIFACT_INFO_KEY) as ArtifactInfo
        storageCredentials = CredentialsUtils.readString(repositoryInfo.storageCredentials?.type, repositoryInfo.storageCredentials?.credentials)
        repositoryConfiguration = JsonUtils.objectMapper.readValue(repositoryInfo.configuration, RepositoryConfiguration::class.java)
        contextAttributes = mutableMapOf()
    }
}
