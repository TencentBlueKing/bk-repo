package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.artifact.config.TRAVERSED_LIST
import com.tencent.bkrepo.common.artifact.pojo.RepositoryIdentify
import com.tencent.bkrepo.common.artifact.pojo.configuration.VirtualConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactTransferContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Component
class PypiVirtualRepository : VirtualRepository() {

    @Suppress("UNCHECKED_CAST")
    private fun getTraversedList(context: ArtifactTransferContext): MutableList<RepositoryIdentify> {
        return context.contextAttributes[TRAVERSED_LIST] as? MutableList<RepositoryIdentify> ?: let {
            val selfRepoInfo = context.repositoryInfo
            val traversedList = mutableListOf(RepositoryIdentify(selfRepoInfo.projectId, selfRepoInfo.name))
            context.contextAttributes[TRAVERSED_LIST] = traversedList
            return traversedList
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(PypiVirtualRepository::class.java)
    }

    /**
     * 整合多个仓库的内容。
     */
    override fun list(context: ArtifactListContext) {
        val artifactInfo = context.artifactInfo
        val virtualConfiguration = context.repositoryConfiguration as VirtualConfiguration

        val repoList = virtualConfiguration.repositoryList
        val traversedList = getTraversedList(context)
        for (repoIdentify in repoList) {
            if (repoIdentify in traversedList) {
                logger.debug("Repository[$repoIdentify] has been traversed, skip it.")
                continue
            }
            traversedList.add(repoIdentify)
            try {
                val subRepoInfo = repositoryResource.detail(repoIdentify.projectId, repoIdentify.name).data!!
                val repository = RepositoryHolder.getRepository(subRepoInfo.category)
                val subContext = pypiArtifactListContextCopy(repositoryInfo = subRepoInfo, contextAttributes = context.contextAttributes)
                // val subContext = context.copy(repositoryInfo = subRepoInfo )
                repository.list(subContext)

            } catch (exception: Exception) {
                logger.warn("Download Artifact[${artifactInfo.getFullUri()}] from Repository[$repoIdentify] failed: ${exception.message}")
            }
        }
    }

    fun pypiArtifactListContextCopy(repositoryInfo: RepositoryInfo,
        contextAttributes: MutableMap<String, Any>
    ): ArtifactListContext {
        val targetContext = ArtifactListContext()
        targetContext.repositoryInfo = repositoryInfo
        targetContext.storageCredentials = repositoryInfo.storageCredentials
        targetContext.repositoryConfiguration = repositoryInfo.configuration
        targetContext.contextAttributes = contextAttributes
        return targetContext
    }
}
