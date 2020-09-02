package com.tencent.bkrepo.common.artifact.repository.virtual

import com.tencent.bkrepo.common.artifact.constant.TRAVERSED_LIST
import com.tencent.bkrepo.common.artifact.pojo.RepositoryIdentify
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * 虚拟仓库抽象逻辑
 */
abstract class VirtualRepository : AbstractArtifactRepository() {

    @Autowired
    lateinit var repositoryClient: RepositoryClient

    override fun <E> search(context: ArtifactSearchContext): List<E> {
        val artifactInfo = context.artifactInfo
        val virtualConfiguration = context.getVirtualConfiguration()
        val repoList = virtualConfiguration.repositoryList
        val traversedList = getTraversedList(context)
        for (repoIdentify in repoList) {
            if (repoIdentify in traversedList) {
                continue
            }
            traversedList.add(repoIdentify)
            try {
                val subRepoDetail = repositoryClient.getRepoDetail(repoIdentify.projectId, repoIdentify.name).data!!
                val repository = ArtifactContextHolder.getRepository(subRepoDetail.category) as AbstractArtifactRepository
                val subContext = context.copy(subRepoDetail) as ArtifactSearchContext
                repository.search<E>(subContext).let {
                    if (logger.isDebugEnabled) {
                        logger.debug("Artifact[$artifactInfo] is found in repository[$repoIdentify].")
                    }
                    return it
                }
            } catch (ignored: Exception) {
                logger.warn("Search Artifact[$artifactInfo] from repository[$repoIdentify] failed: ${ignored.message}")
            }
        }
        return emptyList()
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        val artifactInfo = context.artifactInfo
        val virtualConfiguration = context.getVirtualConfiguration()
        val repoList = virtualConfiguration.repositoryList
        val traversedList = getTraversedList(context)
        for (repoIdentify in repoList) {
            if (repoIdentify in traversedList) {
                if (logger.isDebugEnabled) {
                    logger.debug("Repository[$repoIdentify] has been traversed, skip it.")
                }
                continue
            }
            traversedList.add(repoIdentify)
            try {
                val subRepoDetail = repositoryClient.getRepoDetail(repoIdentify.projectId, repoIdentify.name).data!!
                val repository = ArtifactContextHolder.getRepository(subRepoDetail.category) as AbstractArtifactRepository
                val subContext = context.copy(repositoryDetail = subRepoDetail) as ArtifactDownloadContext
                repository.onDownload(subContext)?.let {
                    if (logger.isDebugEnabled) {
                        logger.debug("Artifact[$artifactInfo] is found in repository[$repoIdentify].")
                    }
                    return it
                } ?: run {
                    if (logger.isDebugEnabled) {
                        logger.debug("Artifact[$artifactInfo] is not found in repository[$repoIdentify], skipped.")
                    }
                }
            } catch (ignored: Exception) {
                logger.warn("Download Artifact[$artifactInfo] from repository[$repoIdentify] failed: ${ignored.message}")
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    protected fun getTraversedList(context: ArtifactContext): MutableList<RepositoryIdentify> {
        return context.getAttribute(TRAVERSED_LIST) as? MutableList<RepositoryIdentify> ?: let {
            val selfRepoInfo = context.repositoryDetail
            val traversedList = mutableListOf(RepositoryIdentify(selfRepoInfo.projectId, selfRepoInfo.name))
            context.putAttribute(TRAVERSED_LIST, traversedList)
            return traversedList
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(VirtualRepository::class.java)
    }
}
