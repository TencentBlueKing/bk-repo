package com.tencent.bkrepo.helm.service.impl

import com.tencent.bkrepo.common.api.util.readYamlString
import com.tencent.bkrepo.common.api.util.toYamlString
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.NODE_CREATE_DATE
import com.tencent.bkrepo.helm.constants.NODE_FULL_PATH
import com.tencent.bkrepo.helm.constants.NODE_METADATA
import com.tencent.bkrepo.helm.constants.NODE_NAME
import com.tencent.bkrepo.helm.constants.NODE_SHA256
import com.tencent.bkrepo.helm.constants.PROJECT_ID
import com.tencent.bkrepo.helm.constants.REPO_NAME
import com.tencent.bkrepo.helm.constants.REPO_TYPE
import com.tencent.bkrepo.helm.constants.TGZ_SUFFIX
import com.tencent.bkrepo.helm.exception.HelmRepoNotFoundException
import com.tencent.bkrepo.helm.model.metadata.HelmIndexYamlMetadata
import com.tencent.bkrepo.helm.utils.HelmUtils
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

abstract class AbstractService {
    @Autowired
    lateinit var nodeClient: NodeClient

    @Autowired
    lateinit var repositoryClient: RepositoryClient

    @Autowired
    lateinit var packageClient: PackageClient

    @Autowired
    lateinit var eventPublisher: ApplicationEventPublisher

    fun getOriginalIndexYaml(): HelmIndexYamlMetadata {
        val context = ArtifactQueryContext()
        context.putAttribute(FULL_PATH, HelmUtils.getIndexYamlFullPath())
        return (ArtifactContextHolder.getRepository().query(context) as ArtifactInputStream).use { it.readYamlString() }
    }

    /**
     * 查询仓库是否存在
     */
    fun checkRepositoryExist(artifactInfo: ArtifactInfo) {
        with(artifactInfo) {
            repositoryClient.getRepoDetail(projectId, repoName, REPO_TYPE).data ?: run {
                logger.error("check repository [$repoName] in projectId [$projectId] failed!")
                throw HelmRepoNotFoundException("repository [$repoName] in projectId [$projectId] not existed.")
            }
        }
    }

    /**
     * 查询节点
     */
    fun queryNodeList(
        artifactInfo: HelmArtifactInfo,
        exist: Boolean = true,
        lastModifyTime: LocalDateTime? = null
    ): List<Map<String, Any?>> {
        with(artifactInfo) {
            val queryModelBuilder = NodeQueryBuilder()
                .select(PROJECT_ID, REPO_NAME, NODE_NAME, NODE_FULL_PATH, NODE_METADATA, NODE_SHA256, NODE_CREATE_DATE)
                .sortByAsc(NODE_FULL_PATH)
                .page(PAGE_NUMBER, PAGE_SIZE)
                .projectId(projectId)
                .repoName(repoName)
                .fullPath(TGZ_SUFFIX, OperationType.SUFFIX)
            if (exist) {
                lastModifyTime?.let { queryModelBuilder.rule(true, NODE_CREATE_DATE, it, OperationType.AFTER) }
            }
            val result = nodeClient.search(queryModelBuilder.build()).data ?: run {
                ChartRepositoryServiceImpl.logger.warn("don't find node list in repository: [$projectId/$repoName].")
                return emptyList()
            }
            return result.records
        }
    }

    /**
     * upload index.yaml file
     */
    fun uploadIndexYamlMetadata(indexYamlMetadata: HelmIndexYamlMetadata) {
        val artifactFile = ArtifactFileFactory.build(indexYamlMetadata.toYamlString().byteInputStream())
        val context = ArtifactUploadContext(artifactFile)
        context.putAttribute(FULL_PATH, HelmUtils.getIndexYamlFullPath())
        ArtifactContextHolder.getRepository().upload(context)
    }

    /**
     * check node exists
     */
    fun exist(projectId: String, repoName: String, fullPath: String): Boolean {
        return nodeClient.checkExist(projectId, repoName, fullPath).data ?: false
    }

    /**
     * 发布事件
     */
    fun publishEvent(any: Any) {
        eventPublisher.publishEvent(any)
    }

    companion object {
        const val PAGE_NUMBER = 0
        const val PAGE_SIZE = 100000
        private val logger: Logger = LoggerFactory.getLogger(AbstractService::class.java)
    }
}
