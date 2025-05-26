package com.tencent.bkrepo.repository.service.repo.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils.ROOT
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.ResourceClearService
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ResourceClearServiceImpl(
    private val nodeService: NodeService,
    private val packageService: PackageService
) : ResourceClearService {

    override fun clearRepo(repository: TRepository, forced: Boolean, operator: String) {
        val projectId = repository.projectId
        val repoName = repository.name
        val supportPackage = repository.type.supportPackage
        if (forced) {
            logger.info("Force clear repository [$projectId/$repoName] by [$operator]")
            if (supportPackage) {
                packageService.deleteAllPackage(projectId, repoName, operator = operator)
            }
        } else {
            // 当仓库类型支持包管理，仓库下没有包时视为空仓库，删除仓库下所有节点
            val isEmpty = if (supportPackage) {
                packageService.getPackageCount(projectId, repoName) == 0L
            } else {
                val artifactInfo = DefaultArtifactInfo(projectId, repoName, ROOT)
                nodeService.countFileNode(artifactInfo) == 0L
            }
            if (!isEmpty) throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_CONTAINS_ARTIFACT)
        }
        nodeService.deleteByPath(projectId, repoName, ROOT, operator)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ResourceClearServiceImpl::class.java)
    }
}
