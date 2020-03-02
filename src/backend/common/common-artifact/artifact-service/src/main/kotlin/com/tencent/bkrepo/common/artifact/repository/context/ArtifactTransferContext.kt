package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.config.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.config.REPO_KEY
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 构件传输context
 * @author: carrypan
 * @date: 2019/11/26
 */
open class ArtifactTransferContext(repo: RepositoryInfo? = null) {
    val request: HttpServletRequest = HttpContextHolder.getRequest()
    val response: HttpServletResponse = HttpContextHolder.getResponse()
    val userId: String
    val artifactInfo: ArtifactInfo
    var repositoryInfo: RepositoryInfo
    var storageCredentials: StorageCredentials? = null
    var repositoryConfiguration: RepositoryConfiguration
    var contextAttributes: MutableMap<String, Any>

    init {
        this.userId = request.getAttribute(USER_KEY) as String
        this.artifactInfo = request.getAttribute(ARTIFACT_INFO_KEY) as ArtifactInfo
        this.repositoryInfo = repo ?: request.getAttribute(REPO_KEY) as RepositoryInfo
        this.storageCredentials = repositoryInfo.storageCredentials
        this.repositoryConfiguration = repositoryInfo.configuration
        this.contextAttributes = mutableMapOf()
    }

    fun copy(repositoryInfo: RepositoryInfo): ArtifactTransferContext {
        val context = this.javaClass.newInstance()
        context.repositoryInfo = repositoryInfo
        context.storageCredentials = repositoryInfo.storageCredentials
        context.repositoryConfiguration = repositoryInfo.configuration
        context.contextAttributes = contextAttributes
        return context
    }
}
