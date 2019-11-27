package com.tencent.bkrepo.common.artifact.repository

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.config.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.config.REPO_KEY
import com.tencent.bkrepo.common.artifact.config.USER_KEY
import com.tencent.bkrepo.common.artifact.repository.configuration.RepositoryConfigurationMapper
import com.tencent.bkrepo.common.storage.core.ClientCredentials
import com.tencent.bkrepo.common.storage.util.CredentialsUtils
import com.tencent.bkrepo.repository.pojo.repo.RepositoryConfiguration
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 构件传输context
 * @author: carrypan
 * @date: 2019/11/26
 */
class ArtifactTransferContext(val file: ArtifactInfo? = null) {
    val request: HttpServletRequest
    val response: HttpServletResponse
    val userId: String
    val repo: RepositoryInfo
    val artifactInfo: ArtifactInfo
    val storageCredentials: ClientCredentials?
    val repositoryConfiguration: RepositoryConfiguration

    init {
        val requestAttributes = RequestContextHolder.getRequestAttributes() as ServletRequestAttributes
        request = requestAttributes.request
        response = requestAttributes.response!!
        userId = request.getAttribute(USER_KEY) as String
        repo = request.getAttribute(REPO_KEY) as RepositoryInfo
        artifactInfo = request.getAttribute(ARTIFACT_INFO_KEY) as ArtifactInfo
        storageCredentials = CredentialsUtils.readString(repo.storageCredentials?.type, repo.storageCredentials?.credentials)
        repositoryConfiguration = RepositoryConfigurationMapper.readString(repo.category, repo.configuration)
    }


}