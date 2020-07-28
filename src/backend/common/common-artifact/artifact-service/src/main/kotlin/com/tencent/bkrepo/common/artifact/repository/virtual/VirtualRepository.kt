package com.tencent.bkrepo.common.artifact.repository.virtual

import com.tencent.bkrepo.common.artifact.config.TRAVERSED_LIST
import com.tencent.bkrepo.common.artifact.pojo.RepositoryIdentify
import com.tencent.bkrepo.common.artifact.pojo.configuration.virtual.VirtualConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactTransferContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 *
 * @author: carrypan
 * @date: 2019/11/26
 */
abstract class VirtualRepository : AbstractArtifactRepository() {

    @Autowired
    lateinit var repositoryResource: RepositoryResource

    override fun search(context: ArtifactSearchContext): Any? {
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
                val repository = RepositoryHolder.getRepository(subRepoInfo.category) as AbstractArtifactRepository
                val subContext = context.copy(repositoryInfo = subRepoInfo) as ArtifactSearchContext
                repository.search(subContext)?.let { jsonObj ->
                    logger.debug("Artifact[$artifactInfo] is found it Repository[$repoIdentify].")
                    return jsonObj
                } ?: logger.debug("Artifact[$artifactInfo] is not found in Repository[$repoIdentify], skipped.")
            } catch (exception: Exception) {
                logger.warn("Search Artifact[$artifactInfo] from Repository[$repoIdentify] failed: ${exception.message}")
            }
        }
        return null
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
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
                val repository = RepositoryHolder.getRepository(subRepoInfo.category) as AbstractArtifactRepository
                val subContext = context.copy(repositoryInfo = subRepoInfo) as ArtifactDownloadContext
                repository.onDownload(subContext)?.let {
                    logger.debug("Artifact[$artifactInfo] is found it Repository[$repoIdentify].")
                    return it
                } ?: logger.debug("Artifact[$artifactInfo] is not found in Repository[$repoIdentify], skipped.")
            } catch (exception: Exception) {
                logger.warn("Download Artifact[$artifactInfo] from Repository[$repoIdentify] failed: ${exception.message}")
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    protected fun getTraversedList(context: ArtifactTransferContext): MutableList<RepositoryIdentify> {
        return context.contextAttributes[TRAVERSED_LIST] as? MutableList<RepositoryIdentify> ?: let {
            val selfRepoInfo = context.repositoryInfo
            val traversedList = mutableListOf(RepositoryIdentify(selfRepoInfo.projectId, selfRepoInfo.name))
            context.contextAttributes[TRAVERSED_LIST] = traversedList
            return traversedList
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(VirtualRepository::class.java)
    }
}
