package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.config.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.config.REPO_KEY
import com.tencent.bkrepo.common.artifact.config.USER_KEY
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.ClientCredentials
import com.tencent.bkrepo.common.storage.util.CredentialsUtils
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.repository.pojo.repo.configuration.RepositoryConfiguration
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 构件传输context
 * @author: carrypan
 * @date: 2019/11/26
 */
open class ArtifactTransferContext {
    val request: HttpServletRequest = HttpContextHolder.getRequest()
    val response: HttpServletResponse = HttpContextHolder.getResponse()
    val userId: String
    val artifactInfo: ArtifactInfo
    var repositoryInfo: RepositoryInfo
    var storageCredentials: ClientCredentials?
    var repositoryConfiguration: RepositoryConfiguration
    var contextAttributes: MutableMap<String, Any>

    init {
        this.userId = request.getAttribute(USER_KEY) as String
        this.artifactInfo = request.getAttribute(ARTIFACT_INFO_KEY) as ArtifactInfo
        this.repositoryInfo = request.getAttribute(REPO_KEY) as RepositoryInfo
        this.storageCredentials = CredentialsUtils.readString(repositoryInfo.storageCredentials?.type, repositoryInfo.storageCredentials?.credentials)
        this.repositoryConfiguration = JsonUtils.objectMapper.readValue(repositoryInfo.configuration, RepositoryConfiguration::class.java)
        this.contextAttributes = mutableMapOf()
    }
}
