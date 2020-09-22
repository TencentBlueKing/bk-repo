package com.tencent.bkrepo.npm.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import com.tencent.bkrepo.npm.constants.SEARCH_REQUEST
import com.tencent.bkrepo.npm.pojo.NpmSearchInfoMap
import com.tencent.bkrepo.npm.pojo.metadata.MetadataSearchRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NpmVirtualRepository : VirtualRepository() {

    @Suppress("UNCHECKED_CAST")
    override fun search(context: ArtifactSearchContext): List<NpmSearchInfoMap> {
        val list = mutableListOf<NpmSearchInfoMap>()
        val searchRequest = context.getAttribute<MetadataSearchRequest>(SEARCH_REQUEST)!!
        val virtualConfiguration = context.getVirtualConfiguration()
        val repoList = virtualConfiguration.repositoryList
        val traversedList = getTraversedList(context)
        for (repoIdentify in repoList) {
            if (repoIdentify in traversedList) {
                continue
            }
            traversedList.add(repoIdentify)
            try {
                val subRepoInfo = repositoryClient.getRepoDetail(repoIdentify.projectId, repoIdentify.name).data!!
                val repository = ArtifactContextHolder.getRepository(subRepoInfo.category) as AbstractArtifactRepository
                val subContext = context.copy(repositoryDetail = subRepoInfo) as ArtifactSearchContext
                repository.search(subContext).let { map ->
                    list.addAll(map as List<NpmSearchInfoMap>)
                }
            } catch (exception: Exception) {
                logger.error("list Artifact[${context.artifactInfo}] from Repository[$repoIdentify] failed: ${exception.message}")
            }
        }
        return list.subList(0, searchRequest.size)
    }


    companion object {
        private val logger = LoggerFactory.getLogger(NpmVirtualRepository::class.java)
    }
}
