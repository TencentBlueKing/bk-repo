package com.tencent.bkrepo.common.artifact.repository.virtual

import com.tencent.bkrepo.common.artifact.config.TRAVERSED_LIST
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.repo.RepositoryIdentify
import com.tencent.bkrepo.repository.pojo.repo.configuration.VirtualConfiguration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.io.File

/**
 *
 * @author: carrypan
 * @date: 2019/11/26
 */
abstract class VirtualRepository : AbstractArtifactRepository {

    @Autowired
    lateinit var repositoryResource: RepositoryResource

    override fun onDownload(context: ArtifactDownloadContext): File? {
        val artifactInfo = context.artifactInfo
        val virtualConfiguration = context.repositoryConfiguration as VirtualConfiguration
        val repoList = virtualConfiguration.repositoryList
        val traversedList = getTraversedList(context)
        for (repoIdentify in repoList) {
            if(repoIdentify in traversedList) {
                logger.debug("Repository[$repoIdentify] has been traversed, skip it.")
                continue
            }
            traversedList.add(repoIdentify)
            try {
                val subRepoInfo = repositoryResource.detail(repoIdentify.projectId, repoIdentify.name).data!!
                val repository = RepositoryHolder.getRepository(subRepoInfo.category) as AbstractArtifactRepository
                val subContext = context.copy(repositoryInfo = subRepoInfo)
                repository.onDownload(subContext)?.let { file ->
                    logger.debug("Artifact[${artifactInfo.getFullUri()}] is found it Repository[$repoIdentify].")
                    return file
                } ?: logger.debug("Artifact[${artifactInfo.getFullUri()}] is not found in Repository[$repoIdentify], skipped.")
            } catch (exception: Exception) {
                logger.warn("Download Artifact[${artifactInfo.getFullUri()}] from Repository[$repoIdentify] failed: ${exception.message}")
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun getTraversedList(context: ArtifactDownloadContext): MutableList<RepositoryIdentify>  {
        return context.contextAttributes[TRAVERSED_LIST] as? MutableList<RepositoryIdentify> ?: let{
            val selfRepoInfo = context.repositoryInfo
            val traversedList = mutableListOf(RepositoryIdentify(selfRepoInfo))
            context.contextAttributes[TRAVERSED_LIST] = traversedList
            return traversedList
        }
    }


    companion object {
        private val logger = LoggerFactory.getLogger(VirtualRepository::class.java)
    }
}
