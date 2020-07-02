package com.tencent.bkrepo.npm.artifact.repository

import com.tencent.bkrepo.common.artifact.pojo.configuration.virtual.VirtualConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import com.tencent.bkrepo.npm.constants.SEARCH_REQUEST
import com.tencent.bkrepo.npm.pojo.NpmSearchResponse
import com.tencent.bkrepo.npm.pojo.metadata.MetadataSearchRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NpmVirtualRepository : VirtualRepository() {
    override fun list(context: ArtifactListContext): NpmSearchResponse {
        val list = mutableListOf<NpmSearchResponse>()
        val searchRequest = context.contextAttributes[SEARCH_REQUEST] as MetadataSearchRequest
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
                val subContext = context.copy(repositoryInfo = subRepoInfo) as ArtifactListContext
                repository.list(subContext)?.let { map ->
                    list.add(map as NpmSearchResponse)
                }
            } catch (exception: RuntimeException) {
                logger.error("list Artifact[$artifactInfo] from Repository[$repoIdentify] failed: ${exception.message}")
            }
        }
        return recordMap(list, searchRequest)
    }

    private fun recordMap(list: List<NpmSearchResponse>, searchRequest: MetadataSearchRequest): NpmSearchResponse {
        if (list.isNullOrEmpty() || list[0].objects.isNullOrEmpty() || list[1].objects.isNullOrEmpty()) return NpmSearchResponse()
        val size = searchRequest.size
        val firstList = list[0].objects
        val secondList = list[1].objects
        return if (firstList.size >= size) {
            NpmSearchResponse(objects = firstList.subList(0, size))
        } else {
            firstList.addAll(secondList)
            if (firstList.size > size) {
                NpmSearchResponse(objects = firstList.subList(0, size))
            } else {
                NpmSearchResponse(objects = firstList)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(VirtualRepository::class.java)
    }
}
