package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.artifact.pojo.configuration.virtual.VirtualConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import com.tencent.bkrepo.pypi.artifact.xml.Value
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PypiVirtualRepository : VirtualRepository(), PypiRepository {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(PypiVirtualRepository::class.java)
    }

    /**
     * 整合多个仓库的内容。
     */
    override fun list(context: ArtifactListContext) {
        val virtualConfiguration = context.repositoryConfiguration as VirtualConfiguration

        val repoList = virtualConfiguration.repositoryList
        val traversedList = getTraversedList(context)
        for (repoIdentify in repoList) {
            if (repoIdentify in traversedList) {
                logger.debug("Repository[$repoIdentify] has been traversed, skip it.")
                continue
            }
            traversedList.add(repoIdentify)
            val subRepoInfo = repositoryResource.detail(repoIdentify.projectId, repoIdentify.name).data!!
            val repository = RepositoryHolder.getRepository(subRepoInfo.category)
            val subContext = context.copy(repositoryInfo = subRepoInfo) as ArtifactListContext
            repository.list(subContext)
        }
    }

    override fun searchNodeList(context: ArtifactSearchContext, xmlString: String): MutableList<Value>? {
        val valueList: MutableList<Value> = mutableListOf()
        val virtualConfiguration = context.repositoryConfiguration as VirtualConfiguration
        val repoList = virtualConfiguration.repositoryList
        val traversedList = getTraversedList(context)
        for (repoIdentify in repoList) {
            if (repoIdentify in traversedList) {
                logger.debug("Repository[$repoIdentify] has been traversed, skip it.")
                continue
            }
            traversedList.add(repoIdentify)
            val subRepoInfo = repositoryResource.detail(repoIdentify.projectId, repoIdentify.name).data!!
            val repository = RepositoryHolder.getRepository(subRepoInfo.category) as PypiRepository
            val subContext = context.copy(subRepoInfo) as ArtifactSearchContext
            val subValueList = repository.searchNodeList(subContext, xmlString)
            subValueList?.let {
                valueList.addAll(it)
            }
        }
        return valueList
    }
}
