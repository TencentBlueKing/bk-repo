package com.tencent.bkrepo.repository.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TMetadata
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.stage.ArtifactStageEnum
import com.tencent.bkrepo.repository.service.StageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 制品晋级服务接口实习那类
 */
@Service
class StageServiceImpl(
    private val nodeDao: NodeDao
) : StageService {

    override fun query(artifactInfo: ArtifactInfo): ArtifactStageEnum {
        val node = findAndCheckNode(artifactInfo)
        return getNodeStage(node)
    }

    override fun upgrade(artifactInfo: ArtifactInfo) {
        val node = findAndCheckNode(artifactInfo)
        val stageMetadata = findStageMetadata(node)
        val oldStage = ArtifactStageEnum.of(stageMetadata.value)
        if (!oldStage.canUpgrade()) {
            throw ErrorCodeException(ArtifactMessageCode.STAGE_UPGRADE_ERROR,  "unsupported stage")
        }
        val newStage = oldStage.upgrade()
        stageMetadata.value = newStage.name
        nodeDao.save(node)
        logger.info("Upgrade stage[$artifactInfo] from $oldStage to $newStage success")
    }

    override fun downgrade(artifactInfo: ArtifactInfo) {
        val node = findAndCheckNode(artifactInfo)
        val stageMetadata = findStageMetadata(node)
        val oldStage = ArtifactStageEnum.of(stageMetadata.value)
        if (!oldStage.canDowngrade()) {
            throw ErrorCodeException(ArtifactMessageCode.STAGE_DOWNGRADE_ERROR,  "unsupported stage")
        }
        val newStage = oldStage.upgrade()
        stageMetadata.value = newStage.name
        nodeDao.save(node)
        logger.info("Downgrade stage[$artifactInfo] from $oldStage to $newStage success")
    }

    private fun findAndCheckNode(artifactInfo: ArtifactInfo): TNode {
        with(artifactInfo) {
            return nodeDao.findNode(projectId, repoName, getArtifactFullPath())
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, getArtifactName())
        }
    }

    private fun getNodeStage(node: TNode): ArtifactStageEnum {
        return ArtifactStageEnum.of(node.metadata?.firstOrNull { it.key == STAGE_METADATA_KEY }?.value)
    }

    private fun findStageMetadata(node: TNode): TMetadata {
        val metadataList = node.metadata ?: run {
            val emptyList = mutableListOf<TMetadata>()
            node.metadata = emptyList
            emptyList
        }
        return metadataList.firstOrNull { it.key == STAGE_METADATA_KEY } ?: run {
            val stageMetadata = TMetadata(STAGE_METADATA_KEY, ArtifactStageEnum.NONE.value)
            metadataList.add(stageMetadata)
            return stageMetadata
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StageServiceImpl::class.java)
        const val STAGE_METADATA_KEY = "_stage"
    }
}